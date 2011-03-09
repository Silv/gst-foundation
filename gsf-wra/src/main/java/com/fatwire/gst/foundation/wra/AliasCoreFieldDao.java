/*
 * Copyright 2010 FatWire Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fatwire.gst.foundation.wra;

import java.util.Collections;
import java.util.Date;

import COM.FutureTense.Interfaces.ICS;

import com.fatwire.assetapi.data.AssetData;
import com.fatwire.assetapi.data.AssetId;
import com.fatwire.cs.core.db.PreparedStmt;
import com.fatwire.cs.core.db.StatementParam;
import com.fatwire.gst.foundation.facade.assetapi.AssetDataUtils;
import com.fatwire.gst.foundation.facade.assetapi.AttributeDataUtils;
import com.fatwire.gst.foundation.facade.ics.ICSFactory;
import com.fatwire.gst.foundation.facade.runtag.asset.Children;
import com.fatwire.gst.foundation.facade.sql.Row;
import com.fatwire.gst.foundation.facade.sql.SqlHelper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static COM.FutureTense.Interfaces.Utilities.goodString;

/**
 * Dao for dealing with core fields in an alias.  Aliases may override
 * fields in their target WRA if it points to another asset.  Aliases
 * may also refer to external URLs.
 *
 * @author Tony Field
 * @since Jul 21, 2010
 */
public class AliasCoreFieldDao {

    private final ICS ics;
    private final WraCoreFieldDao wraCoreFieldDao;

    public AliasCoreFieldDao() {
        this(ICSFactory.getOrCreateICS());
    }

    public AliasCoreFieldDao(ICS ics) {
        this.ics = ics;
        wraCoreFieldDao = new WraCoreFieldDao(ics);
    }

    private static final Log LOG = LogFactory.getLog(AliasCoreFieldDao.class);

    /**
     * Return an AssetData object containing the core fields found in an alias
     * asset.
     * <p/>
     * Also includes selected metadata fields:
     * <ul>
     * <li>id</li>
     * <li>name</li>
     * <li>subtype</li>
     * <li>startdate</li>
     * <li>enddate</li>
     * <li>status</li>
     * </ul>
     *
     * @param id id of alias asset
     * @return AssetData containing core fields for Alias asset
     */
    public AssetData getAsAssetData(AssetId id) {
        return AssetDataUtils.getAssetData(id, "metatitle", "metadescription", "metakeyword", "h1title", "linktext", "path", "template", "id", "name", "subtype", "startdate", "enddate", "status", "target", "target_url", "popup", "linkimage");
    }

    /**
     * Method to test whether or not an asset is an Alias.
     * todo: low priority: optimize as this will be called at runtime
     *
     * @param id asset ID to check
     * @return true if the asset is a valid Alias asset, false if it is not
     */
    public boolean isAlias(AssetId id) {
        try {
            getAlias(id);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Return an alias asset bean given an input id. Required fields must be set
     * or an exception is thrown.
     *
     * @param id asset id
     * @return Alias
     * @see #isAlias
     */
    public Alias getAlias(AssetId id) {
        AssetData data = getAsAssetData(id);
        AssetId target = Children.getOptionalSingleAssociation(ics, id.getType(), Long.toString(id.getId()), "target");
        if (target == null) {
            String targetUrl = AttributeDataUtils.asString(data.getAttributeData("target_url"));
            if (targetUrl != null && targetUrl.length() == 0)
                throw new IllegalStateException("Asset is not an alias because it has neither a target_url attribute nor a target named association: " + id);
            LOG.trace("Alias " + id + " refers to an external URL");

            AliasBeanImpl o = new AliasBeanImpl();
            // Wra fields
            o.setId(id);
            o.setName(AttributeDataUtils.getWithFallback(data, "name"));
            o.setDescription(AttributeDataUtils.asString(data.getAttributeData("description")));
            o.setSubtype(AttributeDataUtils.asString(data.getAttributeData("subtype")));
            o.setStatus(AttributeDataUtils.asString(data.getAttributeData("status")));
            o.setStartDate(AttributeDataUtils.asDate(data.getAttributeData("startdate")));
            o.setEndDate(AttributeDataUtils.asDate(data.getAttributeData("enddate")));
            o.setMetaTitle(AttributeDataUtils.getWithFallback(data, "metatitle"));
            o.setMetaDescription(AttributeDataUtils.getWithFallback(data, "metadescription"));
            o.setMetaKeyword(AttributeDataUtils.getWithFallback(data, "metakeyword"));
            o.setH1Title(AttributeDataUtils.getWithFallback(data, "h1title"));
            o.setLinkText(AttributeDataUtils.getWithFallback(data, "linktext", "h1title"));
            o.setPath(AttributeDataUtils.asString(data.getAttributeData("path")));
            o.setTemplate(AttributeDataUtils.asString(data.getAttributeData("template")));
            // Alias fields
            o.setTargetUrl(AttributeDataUtils.asString(data.getAttributeData("target_url")));
            o.setPopup(AttributeDataUtils.asString(data.getAttributeData("popup")));
            o.setLinkImage(AttributeDataUtils.asAssetId(data.getAttributeData("linkimage")));
            return o;


        } else {
            if (!wraCoreFieldDao.isWebReferenceable(target)) {
                throw new IllegalStateException("Asset is not a valid alias because it refers to a target asset that is not web-referenceable. Alias:" + id + ", target:" + target);
            }
            WebReferenceableAsset wra = wraCoreFieldDao.getWra(target);
            LOG.trace("Alias " + id + " refers to another wra asset: " + target);

            AliasBeanImpl o = new AliasBeanImpl();
            // Wra fields
            o.setId(id);
            o.setName(AttributeDataUtils.getWithFallback(data, "name"));
            o.setDescription(AttributeDataUtils.asString(data.getAttributeData("description")));
            o.setSubtype(AttributeDataUtils.asString(data.getAttributeData("subtype")));
            o.setStatus(AttributeDataUtils.asString(data.getAttributeData("status")));

            Date d = AttributeDataUtils.asDate(data.getAttributeData("startdate"));
            if (d == null) d = wra.getStartDate();
            o.setStartDate(d);

            d = AttributeDataUtils.asDate(data.getAttributeData("enddate"));
            if (d == null) d = wra.getEndDate();
            o.setEndDate(d);

            String s = AttributeDataUtils.getWithFallback(data, "metatitle");
            if (!goodString(s)) s = wra.getMetaTitle();
            o.setMetaTitle(s);

            s = AttributeDataUtils.getWithFallback(data, "metadescription");
            if (!goodString(s)) s = wra.getMetaDescription();
            o.setMetaDescription(s);

            s = AttributeDataUtils.getWithFallback(data, "metakeyword");
            if (!goodString(s)) s = wra.getMetaKeyword();
            o.setMetaKeyword(s);


            s = AttributeDataUtils.getWithFallback(data, "h1title");
            if (!goodString(s)) s = wra.getH1Title();
            o.setH1Title(s);

            s = AttributeDataUtils.getWithFallback(data, "linktext", "h1title");
            if (!goodString(s)) s = wra.getLinkText();
            o.setLinkText(s);


            s = AttributeDataUtils.getWithFallback(data, "path");
            if (!goodString(s)) s = wra.getPath();
            o.setPath(s);

            s = AttributeDataUtils.getWithFallback(data, "template");
            if (!goodString(s)) s = wra.getTemplate();
            o.setTemplate(s);

            // Alias fields
            o.setTarget(AttributeDataUtils.asAssetId(data.getAttributeData("target")));
            o.setPopup(AttributeDataUtils.asString(data.getAttributeData("popup")));
            o.setLinkImage(AttributeDataUtils.asAssetId(data.getAttributeData("linkimage")));

            return o;
        }

    }


    private static final String ASSETPUBLICATION_QRY = "SELECT p.name from Publication p, AssetPublication ap " + "WHERE ap.assettype = ? " + "AND ap.assetid = ? " + "AND ap.pubid=p.id";
    static final PreparedStmt AP_STMT = new PreparedStmt(ASSETPUBLICATION_QRY, Collections.singletonList("AssetPublication")); // todo: low priority: determine
    // why publication
    // cannot fit there.

    static {
        AP_STMT.setElement(0, "AssetPublication", "assettype");
        AP_STMT.setElement(1, "AssetPublication", "assetid");
    }

    public String resolveSite(String c, String cid) {
        final StatementParam param = AP_STMT.newParam();
        param.setString(0, c);
        param.setLong(1, Long.parseLong(cid));
        String result = null;
        for (Row pubid : SqlHelper.select(ics, AP_STMT, param)) {
            if (result != null) {
                LOG.warn("Found asset " + c + ":" + cid + " in more than one publication. It should not be shared; aliases are to be used for cross-site sharing.  Controller will use first site found: " + result);
            } else {
                result = pubid.getString("name");
            }
        }
        return result;
    }

}
