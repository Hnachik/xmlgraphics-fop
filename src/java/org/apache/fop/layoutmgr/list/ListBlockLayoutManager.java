/*
 * $Id: ListBlockLayoutManager.java,v 1.11 2003/03/07 07:58:52 jeremias Exp $
 * ============================================================================
 *                    The Apache Software License, Version 1.1
 * ============================================================================
 * 
 * Copyright (C) 1999-2003 The Apache Software Foundation. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modifica-
 * tion, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment: "This product includes software
 *    developed by the Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 * 
 * 4. The names "FOP" and "Apache Software Foundation" must not be used to
 *    endorse or promote products derived from this software without prior
 *    written permission. For written permission, please contact
 *    apache@apache.org.
 * 
 * 5. Products derived from this software may not be called "Apache", nor may
 *    "Apache" appear in their name, without prior written permission of the
 *    Apache Software Foundation.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * APACHE SOFTWARE FOUNDATION OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLU-
 * DING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ============================================================================
 * 
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Apache Software Foundation and was originally created by
 * James Tauber <jtauber@jtauber.com>. For more information on the Apache
 * Software Foundation, please see <http://www.apache.org/>.
 */ 
package org.apache.fop.layoutmgr.list;

import org.apache.fop.fo.PropertyManager;
import org.apache.fop.layoutmgr.BlockStackingLayoutManager;
import org.apache.fop.layoutmgr.LayoutProcessor;
import org.apache.fop.layoutmgr.LeafPosition;
import org.apache.fop.layoutmgr.BreakPoss;
import org.apache.fop.layoutmgr.LayoutContext;
import org.apache.fop.layoutmgr.PositionIterator;
import org.apache.fop.layoutmgr.BreakPossPosIter;
import org.apache.fop.layoutmgr.Position;
import org.apache.fop.layoutmgr.TraitSetter;
import org.apache.fop.area.Area;
import org.apache.fop.area.Block;
import org.apache.fop.layoutmgr.MinOptMax;
import org.apache.fop.fo.properties.CommonBorderAndPadding;
import org.apache.fop.fo.properties.CommonBackground;

import java.util.ArrayList;
import java.util.List;

/**
 * LayoutManager for a list-block FO.
 * A list block contains list items which are stacked within
 * the list block area..
 */
public class ListBlockLayoutManager extends BlockStackingLayoutManager {
    private CommonBorderAndPadding borderProps = null;
    private CommonBackground backgroundProps;

    private Block curBlockArea;

    private List bodyBreaks = new ArrayList();

    private class SectionPosition extends LeafPosition {
        protected List list;
        protected SectionPosition(LayoutProcessor lm, int pos, List l) {
            super(lm, pos);
            list = l;
        }
    }

    /**
     * Create a new table layout manager.
     *
     */
    public ListBlockLayoutManager() {
    }

    /**
     * @see org.apache.fop.layoutmgr.AbstractLayoutManager#initProperties(PropertyManager)
     */
    protected void initProperties(PropertyManager propMgr) {
        borderProps = propMgr.getBorderAndPadding();
        backgroundProps = propMgr.getBackgroundProps();
    }

    /**
     * Get the next break possibility.
     * The break possibility depends on the height of the header and footer
     * and possible breaks inside the table body.
     *
     * @param context the layout context for finding breaks
     * @return the next break possibility
     */
    public BreakPoss getNextBreakPoss(LayoutContext context) {
        // currently active LM
        LayoutProcessor curLM;

        MinOptMax stackSize = new MinOptMax();
        // if starting add space before
        // stackSize.add(spaceBefore);
        BreakPoss lastPos = null;

        while ((curLM = (LayoutProcessor)getChildLM()) != null) {
            // Make break positions
            // Set up a LayoutContext
            int ipd = context.getRefIPD();
            BreakPoss bp;

            LayoutContext childLC = new LayoutContext(0);
            childLC.setStackLimit(
                  MinOptMax.subtract(context.getStackLimit(),
                                     stackSize));
            childLC.setRefIPD(ipd);

            boolean over = false;
            while (!curLM.isFinished()) {
                if ((bp = curLM.getNextBreakPoss(childLC)) != null) {
                    if (stackSize.opt + bp.getStackingSize().opt > context.getStackLimit().max) {
                        // reset to last break
                        if (lastPos != null) {
                            LayoutProcessor lm = lastPos.getLayoutManager();
                            lm.resetPosition(lastPos.getPosition());
                            if (lm != curLM) {
                                curLM.resetPosition(null);
                            }
                        } else {
                            curLM.resetPosition(null);
                        }
                        over = true;
                        break;
                    }
                    stackSize.add(bp.getStackingSize());
                    lastPos = bp;
                    bodyBreaks.add(bp);

                    if (bp.nextBreakOverflows()) {
                        over = true;
                        break;
                    }

                    childLC.setStackLimit(MinOptMax.subtract(
                                             context.getStackLimit(), stackSize));
                }
            }
            BreakPoss breakPoss = new BreakPoss(
                                    new LeafPosition(this, bodyBreaks.size() - 1));
            if (over) {
                breakPoss.setFlag(BreakPoss.NEXT_OVERFLOWS, true);
            }
            breakPoss.setStackingSize(stackSize);
            return breakPoss;
        }
        setFinished(true);
        return null;
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
        addID();

        // the list block contains areas stacked from each list item

        int listHeight = 0;

        LayoutProcessor childLM;
        int iStartPos = 0;
        LayoutContext lc = new LayoutContext(0);
        while (parentIter.hasNext()) {
            LeafPosition lfp = (LeafPosition) parentIter.next();
            // Add the block areas to Area
            PositionIterator breakPosIter =
              new BreakPossPosIter(bodyBreaks, iStartPos,
                                   lfp.getLeafPos() + 1);
            iStartPos = lfp.getLeafPos() + 1;
            while ((childLM = (LayoutProcessor)breakPosIter.getNextChildLM()) != null) {
                childLM.addAreas(breakPosIter, lc);
            }
        }

        if (borderProps != null) {
            TraitSetter.addBorders(curBlockArea, borderProps);
        }
        if (backgroundProps != null) {
            TraitSetter.addBackground(curBlockArea, backgroundProps);
        }

        flush();

        bodyBreaks.clear();
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
            Area parentArea = parentLM.getParentArea(curBlockArea);
            int referenceIPD = parentArea.getIPD();
            curBlockArea.setIPD(referenceIPD);
            // Get reference IPD from parentArea
            setCurrentArea(curBlockArea); // ??? for generic operations
        }
        return curBlockArea;
    }

    /**
     * Add the child area to this layout manager.
     *
     * @param childArea the child area to add
     */
    public void addChild(Area childArea) {
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
            bodyBreaks.clear();
            reset(null);
        } else {

        }
    }
}

