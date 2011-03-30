/*
 * Copyright 2008 FatWire Corporation. All Rights Reserved.
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

package com.fatwire.gst.foundation.facade.assetapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import COM.FutureTense.Interfaces.ICS;
import COM.FutureTense.Interfaces.IList;

import com.fatwire.assetapi.common.AssetAccessException;
import com.fatwire.assetapi.data.AssetData;
import com.fatwire.assetapi.data.AssetDataManager;
import com.fatwire.assetapi.data.AssetId;
import com.fatwire.assetapi.query.ConditionFactory;
import com.fatwire.assetapi.query.OpTypeEnum;
import com.fatwire.assetapi.query.Query;
import com.fatwire.assetapi.query.SimpleQuery;
import com.fatwire.gst.foundation.facade.runtag.asset.AssetList;
import com.fatwire.system.Session;
import com.fatwire.system.SessionFactory;
import com.openmarket.xcelerate.asset.AssetIdImpl;

/**
 * 
 * This class is a one-stop-shop for all read-only access to AssetData. It acts
 * as a helper class to facilitate {@link AssetDataManager} use in a simplified
 * way in delivery ContentServer templates.
 * <p/>
 * This class is inspired by springframework data access template classes like
 * org.springframework.jdbc.core.JdbcTemplate.
 * <p/>
 * This class is not thread safe and should not be shared between threads.
 * 
 * @author Dolf.Dijkstra
 * @since Nov 23, 2009
 * 
 */
public class AssetAccessTemplate {

    private final Session session;
    private AssetDataManager assetDataManager;

    /**
     * @param session
     */
    public AssetAccessTemplate(final Session session) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null.");
        }
        this.session = session;
    }

    /**
     * Constructor that accepts ICS as an argument.
     * 
     * @param ics
     */
    public AssetAccessTemplate(final ICS ics) {
        if (ics == null) {
            throw new IllegalArgumentException("ics cannot be null.");
        }
        session = SessionFactory.getSession(ics);
    }

    /**
     * Helper method to create an AssetId from c and cid as string values.
     * 
     * @param c
     * @param cid
     * @return
     */
    public AssetId createAssetId(final String c, final String cid) {
        return new AssetIdImpl(c, Long.parseLong(cid));
    }

    /**
     * Helper method to create an AssetId from c and cid as string values.
     * 
     * @param c
     * @param cid
     * @return
     */
    public AssetId createAssetId(final String c, final long cid) {
        return new AssetIdImpl(c, cid);
    }

    /**
     * Method to read an asset and use the AssetMapper to transform the
     * AssetData into another object as specified by the AssetMapper.
     * 
     * @param <T>
     * @param id
     * @param mapper
     * @return
     */
    public <T> T readAsset(final AssetId id, final AssetMapper<T> mapper) {
        final AssetDataManager m = getAssetDataManager();

        T t = null;
        try {
            final Iterable<AssetData> assets = m.read(Arrays.asList(id));
            for (final AssetData assetData : assets) {
                t = mapper.map(assetData);
            }
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }
        return t;
    }

    /**
     * Method to read an asset and use the AssetMapper to transform the
     * AssetData into another object as specified by the AssetMapper interface..
     * 
     * @param <T>
     * @param c the assetType
     * @param cid the asset id
     * @param mapper
     * @return
     */
    public <T> T readAsset(final String c, final String cid, final AssetMapper<T> mapper) {
        return readAsset(this.createAssetId(c, cid), mapper);
    }

    /**
     * Method to read an asset and use the AssetMapper to transform the
     * AssetData into another object as specified by the AssetMapper.
     * 
     * @param <T>
     * @param c the assetType
     * @param cid the asset id
     * @param mapper
     * @return
     */
    public <T> T readAsset(final String c, final long cid, final AssetMapper<T> mapper) {
        return readAsset(new AssetIdImpl(c, cid), mapper);
    }

    /**
     * Method to read an asset and use the AssetMapper to transform the
     * AssetData into another object as specified by the AssetMapper. Only the
     * list of lister attributes is retrieved from the asset.
     * 
     * @param <T>
     * @param id
     * @param mapper
     * @param attributes
     * @return
     */
    public <T> T readAsset(final AssetId id, final AssetMapper<T> mapper, final String[] attributes) {
        final AssetDataManager m = getAssetDataManager();

        T t = null;
        try {
            final AssetData asset = m.readAttributes(id, Arrays.asList(attributes));
            t = mapper.map(asset);
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }
        return t;
    }

    /**
     * Method to read an asset and pass the results to the closure for further
     * handling.
     * 
     * @param id
     * @param closure
     */
    public void readAsset(final AssetId id, final AssetClosure closure) {
        final AssetDataManager m = getAssetDataManager();

        try {
            final Iterable<AssetData> assets = m.read(Arrays.asList(id));
            for (final AssetData assetData : assets) {
                closure.work(assetData);

            }
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }

    }

    /**
     * Reads an asset based on the listed attribute names
     * <p/>
     * TODO: do we need to load the attribute values and prevent access to
     * non-listed attributes (prevent lazy loading)
     * 
     * @param id the assetid
     * @param attributes the list of attributes to return
     * @return the asset found
     * 
     * 
     */
    public AssetData readAsset(final AssetId id, final String... attributes) {
        final AssetDataManager m = getAssetDataManager();

        Iterable<AssetData> assets;
        try {
            assets = m.read(Arrays.asList(id));
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }
        if (assets == null) {
            return null;
        }
        if (assets instanceof List<?>) {
            final List<AssetData> x = (List<AssetData>) assets;
            if (x.isEmpty()) {
                return null;
            } else {
                return x.get(0);
            }
        }
        final Iterator<AssetData> i = assets.iterator();
        if (i.hasNext()) {
            return i.next();
        }
        return null;
    }

    /**
     * @return
     */
    protected AssetDataManager getAssetDataManager() {
        if (assetDataManager == null) {
            assetDataManager = (AssetDataManager) session.getManager(AssetDataManager.class.getName());
        }
        return assetDataManager;
    }

    /**
     * @param id
     * @return
     */
    public AssetData readAsset(final AssetId id) {

        final AssetDataManager m = getAssetDataManager();

        Iterable<AssetData> assets;
        try {
            assets = m.read(Collections.singletonList(id));
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }
        if (assets == null) {
            return null;
        }
        if (assets instanceof List<?>) {
            final List<AssetData> x = (List<AssetData>) assets;
            if (x.isEmpty()) {
                return null;
            } else {
                return x.get(0);
            }
        }
        final Iterator<AssetData> i = assets.iterator();
        if (i.hasNext()) {
            return i.next();
        }
        return null;
    }

    /**
     * @param query
     * @return
     */
    public Iterable<AssetData> readAssets(final Query query) {
        final AssetDataManager m = getAssetDataManager();

        Iterable<AssetData> assets;
        try {
            assets = m.read(query);
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }

        return assets;
    }

    
    /**
     * Invokes the work(asset) method on the provided Closure for assets returned by the Query.
     * 
     * @param query
     * @param closure
     */
    public void readAssets(final Query query, final AssetClosure closure) {
        final AssetDataManager m = getAssetDataManager();

        try {
            for (AssetData asset : m.read(query)) {
                closure.work(asset);
            }
        } catch (final AssetAccessException e) {
            throw new RuntimeAssetAccessException(e);
        }
    }

    /**
     * Reading assets with the Query and using the mapper to transform the
     * AssetData into another object, as specified by T.
     * 
     * @param <T>
     * @param query
     * @param mapper
     * @return
     */
    public <T> Iterable<T> readAssets(final Query query, final AssetMapper<T> mapper) {

        final List<T> r = new LinkedList<T>();

        for (final AssetData data : readAssets(query)) {
            r.add(mapper.map(data));
        }

        return r;
    }

    /**
     * @param ics
     * @param assetType
     * @param name
     * @return
     */
    public AssetId findByName(final ICS ics, final String assetType, final String name) {
        // TODO: name does not need to be unique, how do we handle this?
        final AssetList x = new AssetList();
        x.setType(assetType);
        x.setField("name", name);
        x.setList("name__");
        x.execute(ics);

        final IList list = ics.GetList("name__");
        ics.RegisterList("name__", null);
        if (list != null && list.hasData()) {
            list.moveTo(1);
            try {
                return new AssetIdImpl(assetType, Long.parseLong(list.getValue("id")));
            } catch (final NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }

    }

    /**
     * Creates a Query to retrieve the asset by it's name.
     * 
     * @param assetType
     * @param assetName
     * @return
     */
    public SimpleQuery createNameQuery(final String assetType, final String assetName) {
        final SimpleQuery q = new SimpleQuery(assetType, null, ConditionFactory.createCondition("name",
                OpTypeEnum.EQUALS, assetName), Arrays.asList("id"));
        q.getProperties().setIsBasicSearch(true);
        return q;
    }

}
