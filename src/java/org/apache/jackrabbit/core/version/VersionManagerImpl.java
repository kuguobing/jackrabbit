/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.version;

import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.Constants;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.ItemStateManager;
import org.apache.jackrabbit.core.virtual.VirtualItemStateProvider;
import org.apache.log4j.Logger;

import javax.jcr.RepositoryException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import java.util.Iterator;
import java.util.List;

/**
 * This Class implements a VersionManager. It more or less acts as proxy
 * between the virtual item state manager that exposes the version to the
 * version storage ({@link VersionItemStateProvider}) and the persistent
 * version manager.
 */
public class VersionManagerImpl implements VersionManager, Constants, InternalVersionItemListener {

    /**
     * the default logger
     */
    private static Logger log = Logger.getLogger(VersionManager.class);

    /**
     * The root node UUID for the version storage
     */
    private final String VERSION_STORAGE_NODE_UUID;

    /**
     * The root parent node UUID for the version storage
     */
    private final String VERSION_STORAGE_PARENT_NODE_UUID;

    /**
     * The version manager of the internal versions
     */
    private final PersistentVersionManager vMgr;
    /**
     * The virtual item manager that exposes the versions to the content
     */
    private VersionItemStateProvider virtProvider;
    /**
     * the node type manager
     */
    private NodeTypeRegistry ntReg;


    /**
     * Creates a bew vesuion manager
     *
     * @param vMgr
     */
    public VersionManagerImpl(PersistentVersionManager vMgr, NodeTypeRegistry ntReg,
                              String rootUUID, String rootParentUUID) {
        this.vMgr = vMgr;
        this.ntReg = ntReg;
        this.VERSION_STORAGE_NODE_UUID = rootUUID;
        this.VERSION_STORAGE_PARENT_NODE_UUID = rootParentUUID;
    }

    /**
     * returns the virtual item state provider that exposes the internal versions
     * as items.
     *
     * @param base
     * @return
     */
    public synchronized VirtualItemStateProvider getVirtualItemStateProvider(ItemStateManager base) {
        if (virtProvider == null) {
            try {
                // init the definition id mgr
                virtProvider = new VersionItemStateProvider(this, ntReg, VERSION_STORAGE_NODE_UUID, VERSION_STORAGE_PARENT_NODE_UUID);
            } catch (Exception e) {
                // todo: better error handling
                log.error("Error while initializing virtual items.", e);
                throw new IllegalStateException(e.toString());
            }
        }
        return virtProvider;
    }

    /**
     * Close this version manager. After having closed a persistence
     * manager, further operations on this object are treated as illegal
     * and throw
     *
     * @throws Exception if an error occurs
     */
    public void close() throws Exception {
    }

    /**
     * Creates a new version history. This action is needed either when creating
     * a new 'mix:versionable' node or when adding the 'mix:versionalbe' mixin
     * to a node.
     *
     * @param node
     * @return
     * @throws javax.jcr.RepositoryException
     */
    public VersionHistory createVersionHistory(NodeImpl node) throws RepositoryException {
        InternalVersionHistory history = vMgr.createVersionHistory(node);
        history.addListener(this);
        onVersionStorageChanged();
        return (VersionHistory) node.getSession().getNodeByUUID(history.getId());
    }

    /**
     * Checks if the version history with the given id exists
     *
     * @param id
     * @return
     */
    public boolean hasVersionHistory(String id) {
        return vMgr.hasVersionHistory(id);
    }

    /**
     * Returns the version history with the given id
     *
     * @param id
     * @return
     * @throws RepositoryException
     */
    public InternalVersionHistory getVersionHistory(String id) throws RepositoryException {
        InternalVersionHistory hist = vMgr.getVersionHistory(id);
        if (hist != null) {
            hist.addListener(this);
        }
        return hist;
    }

    /**
     * Returns the number of version histories
     *
     * @return
     * @throws RepositoryException
     */
    public int getNumVersionHistories() throws RepositoryException {
        return vMgr.getNumVersionHistories();
    }

    /**
     * Returns an iterator over all ids of {@link InternalVersionHistory}s.
     *
     * @return
     * @throws RepositoryException
     */
    public Iterator getVersionHistoryIds() throws RepositoryException {
        return vMgr.getVersionHistoryIds();
    }

    /**
     * Checks if the version with the given id exists
     *
     * @param id
     * @return
     */
    public boolean hasVersion(String id) {
        return vMgr.hasVersion(id);
    }

    /**
     * Returns the version with the given id
     *
     * @param id
     * @return
     * @throws RepositoryException
     */
    public InternalVersion getVersion(String id) throws RepositoryException {
        InternalVersion vers = vMgr.getVersion(id);
        if (vers != null) {
            vers.addListener(this);
        }
        return vers;
    }

    /**
     * checks, if the node with the given id exists
     *
     * @param id
     * @return
     */
    public boolean hasItem(String id) {
        return vMgr.hasItem(id);
    }

    /**
     * Returns the version item with the given id
     *
     * @param id
     * @return
     * @throws RepositoryException
     */
    public InternalVersionItem getItem(String id) throws RepositoryException {
        InternalVersionItem item = vMgr.getItemByExternal(id);
        if (item != null) {
            item.addListener(this);
        }
        return item;
    }

    /**
     * invokes the checkin() on the persistent version manager and remaps the
     * newly created version objects.
     *
     * @param node
     * @return
     * @throws RepositoryException
     */
    public Version checkin(NodeImpl node) throws RepositoryException {
        InternalVersion version = vMgr.checkin(node);
        version.addListener(this);
        return (Version) node.getSession().getNodeByUUID(version.getId());
    }

    /**
     * {@inheritDoc}
     */
    public List getItemReferences(InternalVersionItem item) {
        return vMgr.getItemReferences(item);
    }

    /**
     * {@inheritDoc}
     */
    public void setItemReferences(InternalVersionItem item, List references) {
        vMgr.setItemReferences(item, references);
    }

    /**
     * {@inheritDoc}
     */
    public void itemModifed(InternalVersionItem item) {
        try {
            virtProvider.getItemState(new NodeId(item.getId())).discard();
        } catch (ItemStateException e) {
            log.error("Error while refreshing virtual item.", e);
        }
    }

    /**
     * Flushes the virtual node state information of the version storage
     */
    public void onVersionStorageChanged() {
        try {
            virtProvider.getItemState(new NodeId(VERSION_STORAGE_NODE_UUID)).discard();
        } catch (ItemStateException e) {
            log.error("Error while refreshing virtual version storage.", e);
        }
    }
}
