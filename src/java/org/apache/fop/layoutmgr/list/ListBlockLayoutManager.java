/*
 * Copyright 1999-2005 The Apache Software Foundation.
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

/* $Id$ */
 
package org.apache.fop.layoutmgr.list;

import org.apache.fop.fo.flow.ListBlock;
import org.apache.fop.layoutmgr.BlockLevelLayoutManager;
import org.apache.fop.layoutmgr.BlockStackingLayoutManager;
import org.apache.fop.layoutmgr.LayoutManager;
import org.apache.fop.layoutmgr.LayoutContext;
import org.apache.fop.layoutmgr.PositionIterator;
import org.apache.fop.layoutmgr.Position;
import org.apache.fop.layoutmgr.NonLeafPosition;
import org.apache.fop.layoutmgr.TraitSetter;
import org.apache.fop.area.Area;
import org.apache.fop.area.Block;
import org.apache.fop.traits.MinOptMax;
import org.apache.fop.traits.SpaceVal;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * LayoutManager for a list-block FO.
 * A list block contains list items which are stacked within
 * the list block area..
 */
public class ListBlockLayoutManager extends BlockStackingLayoutManager {
    private Block curBlockArea;

    //TODO space-before|after: handle space-resolution rules
    private MinOptMax spaceBefore;
    private MinOptMax spaceAfter;

    private static class StackingIter extends PositionIterator {
        StackingIter(Iterator parentIter) {
            super(parentIter);
        }

        protected LayoutManager getLM(Object nextObj) {
            return ((Position) nextObj).getLM();
        }

        protected Position getPos(Object nextObj) {
            return ((Position) nextObj);
        }
    }

    /*
    private class SectionPosition extends LeafPosition {
        protected List list;
        protected SectionPosition(LayoutManager lm, int pos, List l) {
            super(lm, pos);
            list = l;
        }
    }*/

    /**
     * Create a new list block layout manager.
     * @param node list-block to create the layout manager for
     */
    public ListBlockLayoutManager(ListBlock node) {
        super(node);
        initialize();
    }

    /**
     * Convenience method.
     * @return the ListBlock node
     */
    protected ListBlock getListBlockFO() {
        return (ListBlock)fobj;
    }

    private void initialize() {
        spaceBefore = new SpaceVal(getListBlockFO().getCommonMarginBlock().spaceBefore).getSpace();
        spaceAfter = new SpaceVal(getListBlockFO().getCommonMarginBlock().spaceAfter).getSpace();
    }

    private int getIPIndents() {
        int iIndents = 0;
        iIndents += getListBlockFO().getCommonMarginBlock().startIndent.getValue();
        iIndents += getListBlockFO().getCommonMarginBlock().endIndent.getValue();
        return iIndents;
    }
    
    /** @see org.apache.fop.layoutmgr.LayoutManager#getChangedKnuthElements(java.util.List, int) */
    public LinkedList getChangedKnuthElements(List oldList, int alignment) {
        //log.debug("LBLM.getChangedKnuthElements>");
        return super.getChangedKnuthElements(oldList, alignment);
    }

    /**
     * The table area is a reference area that contains areas for
     * columns, bodies, rows and the contents are in cells.
     *
     * @param parentIter the position iterator
     * @param layoutContext the layout context for adding areas
     */
    public void addAreas(PositionIterator parentIter,
                         LayoutContext layoutContext) {
        getParentArea(null);
        
        // if adjusted space before
        double adjust = layoutContext.getSpaceAdjust();
        addBlockSpacing(adjust, spaceBefore);
        spaceBefore = null;
        
        getPSLM().addIDToPage(getListBlockFO().getId());

        // the list block contains areas stacked from each list item

        LayoutManager childLM = null;
        LayoutContext lc = new LayoutContext(0);
        LayoutManager firstLM = null;
        LayoutManager lastLM = null;
        Position firstPos = null;
        Position lastPos = null;

        // "unwrap" the NonLeafPositions stored in parentIter
        // and put them in a new list; 
        LinkedList positionList = new LinkedList();
        Position pos;
        while (parentIter.hasNext()) {
            pos = (Position)parentIter.next();
            if (pos.getIndex() >= 0) {
                if (firstPos == null) {
                    firstPos = pos;
                }
                lastPos = pos;
            }
            if (pos instanceof NonLeafPosition
                    && (pos.getPosition() != null)
                    && ((NonLeafPosition) pos).getPosition().getLM() != this) {
                // pos was created by a child of this ListBlockLM
                positionList.add(((NonLeafPosition) pos).getPosition());
                lastLM = ((NonLeafPosition) pos).getPosition().getLM();
                if (firstLM == null) {
                    firstLM = lastLM;
                }
            }
        }

        if (markers != null) {
            getCurrentPV().addMarkers(markers, true, isFirst(firstPos), isLast(lastPos));
        }

        StackingIter childPosIter = new StackingIter(positionList.listIterator());
        while ((childLM = childPosIter.getNextChildLM()) != null) {
            // Add the block areas to Area
            lc.setFlags(LayoutContext.FIRST_AREA, childLM == firstLM);
            lc.setFlags(LayoutContext.LAST_AREA, childLM == lastLM);
            lc.setStackLimit(layoutContext.getStackLimit());
            childLM.addAreas(childPosIter, lc);
        }

        if (markers != null) {
            getCurrentPV().addMarkers(markers, false, isFirst(firstPos), isLast(lastPos));
        }

        flush();

        // if adjusted space after
        addBlockSpacing(adjust, spaceAfter);
        
        curBlockArea = null;
    }

    /**
     * Return an Area which can contain the passed childArea. The childArea
     * may not yet have any content, but it has essential traits set.
     * In general, if the LayoutManager already has an Area it simply returns
     * it. Otherwise, it makes a new Area of the appropriate class.
     * It gets a parent area for its area by calling its parent LM.
     * Finally, based on the dimensions of the parent area, it initializes
     * its own area. This includes setting the content IPD and the maximum
     * BPD.
     *
     * @param childArea the child area
     * @return the parent area of the child
     */
    public Area getParentArea(Area childArea) {
        if (curBlockArea == null) {
            curBlockArea = new Block();
            
            // Set up dimensions
            // Must get dimensions from parent area
            /*Area parentArea =*/ parentLM.getParentArea(curBlockArea);

            // set traits
            TraitSetter.addBorders(curBlockArea, 
                    getListBlockFO().getCommonBorderPaddingBackground());
            TraitSetter.addBackground(curBlockArea, 
                    getListBlockFO().getCommonBorderPaddingBackground());
            TraitSetter.addMargins(curBlockArea,
                    getListBlockFO().getCommonBorderPaddingBackground(), 
                    getListBlockFO().getCommonMarginBlock());
            TraitSetter.addBreaks(curBlockArea, 
                    getListBlockFO().getBreakBefore(), 
                    getListBlockFO().getBreakAfter());
            
            int contentIPD = referenceIPD - getIPIndents();
            curBlockArea.setIPD(contentIPD);
            
            setCurrentArea(curBlockArea);
        }
        return curBlockArea;
    }

    /**
     * Add the child area to this layout manager.
     *
     * @param childArea the child area to add
     */
    public void addChildArea(Area childArea) {
        if (curBlockArea != null) {
            curBlockArea.addBlock((Block) childArea);
        }
    }

    /**
     * Reset the position of this layout manager.
     *
     * @param resetPos the position to reset to
     */
    public void resetPosition(Position resetPos) {
        if (resetPos == null) {
            reset(null);
        } else {
            //TODO Something to put here?
        }
    }
    
    /** @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#mustKeepTogether() */
    public boolean mustKeepTogether() {
        //TODO Keeps will have to be more sophisticated sooner or later
        return ((BlockLevelLayoutManager)getParent()).mustKeepTogether() 
                || !getListBlockFO().getKeepTogether().getWithinPage().isAuto()
                || !getListBlockFO().getKeepTogether().getWithinColumn().isAuto();
    }

    /** @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#mustKeepWithPrevious() */
    public boolean mustKeepWithPrevious() {
        return !getListBlockFO().getKeepWithPrevious().getWithinPage().isAuto()
            || !getListBlockFO().getKeepWithPrevious().getWithinColumn().isAuto();
    }

    /** @see org.apache.fop.layoutmgr.BlockLevelLayoutManager#mustKeepWithNext() */
    public boolean mustKeepWithNext() {
        return !getListBlockFO().getKeepWithNext().getWithinPage().isAuto()
                || !getListBlockFO().getKeepWithNext().getWithinColumn().isAuto();
    }

}

