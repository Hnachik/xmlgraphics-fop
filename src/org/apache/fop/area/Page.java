/*
 * $Id$
 * Copyright (C) 2001 The Apache Software Foundation. All rights reserved.
 * For details on use and redistribution please refer to the
 * LICENSE file included with these sources.
 */

package org.apache.fop.area;

import java.io.Serializable;
import java.util.HashMap;

/**
 * The page.
 * This holds the contents of the page. Each region is added.
 * The unresolved references area added so that if the page is
 * serialized then it will handle the resolving properly after
 * being reloaded.
 * This is serializable so it can be saved to cache to save
 * memory if there are forward references.
 * The page is cloneable so the page master can make copies of
 * the top level page and regions.
 */
public class Page implements Serializable, Cloneable {
    // contains before, start, body, end and after regions
    RegionViewport regionBefore = null;
    RegionViewport regionStart = null;
    RegionViewport regionBody = null;
    RegionViewport regionEnd = null;
    RegionViewport regionAfter = null;

    // hashmap of markers for this page
    // start and end are added by the fo that contains the markers
    HashMap markerStart = null;
    HashMap markerEnd = null;

    private HashMap unresolved = null;

    public void setRegion(int areaclass, RegionViewport port) {
        if (areaclass == RegionReference.BEFORE) {
            regionBefore = port;
        } else if (areaclass == RegionReference.START) {
            regionStart = port;
        } else if (areaclass == RegionReference.BODY) {
            regionBody = port;
        } else if (areaclass == RegionReference.END) {
            regionEnd = port;
        } else if (areaclass == RegionReference.AFTER) {
            regionAfter = port;
        }
    }

    public RegionViewport getRegion(int areaclass) {
        if (areaclass == RegionReference.BEFORE) {
            return regionBefore;
        } else if (areaclass == RegionReference.START) {
            return regionStart;
        } else if (areaclass == RegionReference.BODY) {
            return regionBody;
        } else if (areaclass == RegionReference.END) {
            return regionEnd;
        } else if (areaclass == RegionReference.AFTER) {
            return regionAfter;
        }
        return null;
    }

    public Object clone() {
        Page p = new Page();
        if(regionBefore != null)
            p.regionBefore = (RegionViewport)regionBefore.clone();
        if(regionStart != null)
            p.regionStart = (RegionViewport)regionStart.clone();
        if(regionBody != null)
            p.regionBody = (RegionViewport)regionBody.clone();
        if(regionEnd != null)
            p.regionEnd = (RegionViewport)regionEnd.clone();
        if(regionAfter != null)
            p.regionAfter = (RegionViewport)regionAfter.clone();

        return p;
    }

    public void setUnresolvedReferences(HashMap unres) {
        unresolved = unres;
    }
    
    public HashMap getUnresolvedReferences() {
        return unresolved;
    }
}

