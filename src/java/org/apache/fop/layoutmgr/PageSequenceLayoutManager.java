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

package org.apache.fop.layoutmgr;

import org.apache.fop.apps.FOPException;

import org.apache.fop.area.AreaTreeHandler;
import org.apache.fop.area.AreaTreeModel;
import org.apache.fop.area.Block;
import org.apache.fop.area.Footnote;
import org.apache.fop.area.PageViewport;
import org.apache.fop.area.LineArea;
import org.apache.fop.area.Resolvable;

import org.apache.fop.fo.Constants;
import org.apache.fop.fo.flow.Marker;
import org.apache.fop.fo.flow.RetrieveMarker;
import org.apache.fop.fo.pagination.Flow;
import org.apache.fop.fo.pagination.PageSequence;
import org.apache.fop.fo.pagination.Region;
import org.apache.fop.fo.pagination.SideRegion;
import org.apache.fop.fo.pagination.SimplePageMaster;
import org.apache.fop.fo.pagination.StaticContent;

import org.apache.fop.traits.MinOptMax;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * LayoutManager for a PageSequence.  This class is instantiated by
 * area.AreaTreeHandler for each fo:page-sequence found in the
 * input document.
 */
public class PageSequenceLayoutManager extends AbstractLayoutManager {

    /** 
     * AreaTreeHandler which activates the PSLM and controls
     * the rendering of its pages.
     */
    private AreaTreeHandler areaTreeHandler;

    /** 
     * fo:page-sequence formatting object being
     * processed by this class
     */
    private PageSequence pageSeq;

    private PageViewportProvider pvProvider;
    
    /** 
     * Current page-viewport-area being filled by
     * the PSLM.
     */
    private PageViewport curPV = null;

    /**
     * The FlowLayoutManager object, which processes
     * the single fo:flow of the fo:page-sequence
     */
    private FlowLayoutManager childFLM = null;

    private int startPageNum = 0;
    private int currentPageNum = 0;

    private Block separatorArea = null;
    
    /**
     * Constructor
     *
     * @param ath the area tree handler object
     * @param pseq fo:page-sequence to process
     */
    public PageSequenceLayoutManager(AreaTreeHandler ath, PageSequence pseq) {
        super(pseq);
        this.areaTreeHandler = ath;
        this.pageSeq = pseq;
        this.pvProvider = new PageViewportProvider(this.pageSeq);
    }

    /**
     * @see org.apache.fop.layoutmgr.LayoutManager
     * @return the LayoutManagerMaker object
     */
    public LayoutManagerMaker getLayoutManagerMaker() {
        return areaTreeHandler.getLayoutManagerMaker();
    }

    /** @return the PageViewportProvider applicable to this page-sequence. */
    public PageViewportProvider getPageViewportProvider() {
        return this.pvProvider;
    }
    
    /**
     * Activate the layout of this page sequence.
     * PageViewports corresponding to each page generated by this 
     * page sequence will be created and sent to the AreaTreeModel
     * for rendering.
     */
    public void activateLayout() {
        startPageNum = pageSeq.getStartingPageNumber();
        currentPageNum = startPageNum - 1;

        LineArea title = null;

        if (pageSeq.getTitleFO() != null) {
            ContentLayoutManager clm = new ContentLayoutManager(pageSeq
                    .getTitleFO(), this);
            title = (LineArea) clm.getParentArea(null);
        }

        areaTreeHandler.getAreaTreeModel().startPageSequence(title);
        log.debug("Starting layout");

        curPV = makeNewPage(false, false);

        Flow mainFlow = pageSeq.getMainFlow();
        childFLM = getLayoutManagerMaker().
            makeFlowLayoutManager(this, mainFlow);

        PageBreaker breaker = new PageBreaker(this);
        int flowBPD = (int) curPV.getBodyRegion().getBPD();
        breaker.doLayout(flowBPD);
        
        finishPage();
        pageSeq.getRoot().notifyPageSequenceFinished(currentPageNum,
                (currentPageNum - startPageNum) + 1);
        log.debug("Ending layout");
    }

    private class PageBreaker extends AbstractBreaker {
        
        private PageSequenceLayoutManager pslm;
        private boolean firstPart = true;
        
        private StaticContentLayoutManager footnoteSeparatorLM = null;

        public PageBreaker(PageSequenceLayoutManager pslm) {
            this.pslm = pslm;
        }
        
        protected LayoutContext createLayoutContext() {
            LayoutContext lc = new LayoutContext(0);
            int flowIPD = curPV.getCurrentSpan().getColumnWidth();
            lc.setRefIPD(flowIPD);
            return lc;
        }
        
        protected LayoutManager getTopLevelLM() {
            return null;  // unneeded for PSLM
        }
        
        /** @see org.apache.fop.layoutmgr.AbstractBreaker#getPageViewportProvider() */
        protected PageSequenceLayoutManager.PageViewportProvider getPageViewportProvider() {
            return pvProvider;
        }
        
        protected LinkedList getNextKnuthElements(LayoutContext context, int alignment) {
            LinkedList contentList = null;
            
            while (!childFLM.isFinished() && contentList == null) {
                contentList = childFLM.getNextKnuthElements(context, alignment);
            }

            // scan contentList, searching for footnotes
            boolean bFootnotesPresent = false;
            if (contentList != null) {
                ListIterator contentListIterator = contentList.listIterator();
                while (contentListIterator.hasNext()) {
                    KnuthElement element = (KnuthElement) contentListIterator.next();
                    if (element instanceof KnuthBlockBox
                        && ((KnuthBlockBox) element).hasAnchors()) {
                        // element represents a line with footnote citations
                        bFootnotesPresent = true;
                        LinkedList footnoteBodyLMs = ((KnuthBlockBox) element).getFootnoteBodyLMs();
                        ListIterator footnoteBodyIterator = footnoteBodyLMs.listIterator();
                        // store the lists of elements representing the footnote bodies
                        // in the box representing the line containing their references
                        while (footnoteBodyIterator.hasNext()) {
                            FootnoteBodyLayoutManager fblm 
                                = (FootnoteBodyLayoutManager) footnoteBodyIterator.next();
                            fblm.setParent(childFLM);
                            ((KnuthBlockBox) element).addElementList(
                                    fblm.getNextKnuthElements(context, alignment));
                        }
                    }
                }
            }

            // handle the footnote separator
            StaticContent footnoteSeparator;
            if (bFootnotesPresent
                    && (footnoteSeparator = pageSeq.getStaticContent(
                                            "xsl-footnote-separator")) != null) {
                // the footnote separator can contain page-dependent content such as
                // page numbers or retrieve markers, so its areas cannot simply be 
                // obtained now and repeated in each page;
                // we need to know in advance the separator bpd: the actual separator
                // could be different from page to page, but its bpd would likely be
                // always the same

                // create a Block area that will contain the separator areas
                separatorArea = new Block();
                separatorArea.setIPD(context.getRefIPD());
                // create a StaticContentLM for the footnote separator
                footnoteSeparatorLM = (StaticContentLayoutManager)
                    getLayoutManagerMaker().makeStaticContentLayoutManager(
                    pslm, footnoteSeparator, separatorArea);
                footnoteSeparatorLM.doLayout();

                footnoteSeparatorLength = new MinOptMax(separatorArea.getBPD());
            }
            return contentList;
        }
        
        protected int getCurrentDisplayAlign() {
            return curPV.getSPM().getRegion(Constants.FO_REGION_BODY).getDisplayAlign();
        }
        
        protected boolean hasMoreContent() {
            return !childFLM.isFinished();
        }
        
        protected void addAreas(PositionIterator posIter, LayoutContext context) {
            if (footnoteSeparatorLM != null) {
                StaticContent footnoteSeparator = pageSeq.getStaticContent(
                        "xsl-footnote-separator");
                // create a Block area that will contain the separator areas
                separatorArea = new Block();
                separatorArea.setIPD(curPV.getCurrentSpan().getColumnWidth());
                // create a StaticContentLM for the footnote separator
                footnoteSeparatorLM = (StaticContentLayoutManager)
                    getLayoutManagerMaker().makeStaticContentLayoutManager(
                    pslm, footnoteSeparator, separatorArea);
                footnoteSeparatorLM.doLayout();
            }

            childFLM.addAreas(posIter, context);    
        }
        
        protected void doPhase3(PageBreakingAlgorithm alg, int partCount, 
                BlockSequence originalList, BlockSequence effectiveList) {
            //Directly add areas after finding the breaks
            addAreas(alg, partCount, originalList, effectiveList);
        }
        
        protected void startPart(BlockSequence list, int breakClass) {
            if (curPV == null) {
                throw new IllegalStateException("curPV must not be null");
            } else {
                //firstPart is necessary because we need the first page before we start the 
                //algorithm so we have a BPD and IPD. This may subject to change later when we
                //start handling more complex cases.
                if (!firstPart) {
                    if (curPV.getCurrentSpan().hasMoreFlows()) {
                        curPV.getCurrentSpan().moveToNextFlow();
                    } else  {
                        // if this is the first page that will be created by
                        // the current BlockSequence, it could have a break
                        // condition that must be satisfied;
                        // otherwise, we may simply need a new page
                        handleBreakTrait(breakClass);
                    }
                }
                pvProvider.setStartPageOfNextElementList(currentPageNum);
            }
            // add static areas and resolve any new id areas
            // finish page and add to area tree
            firstPart = false;
        }
        
        protected void finishPart(PageBreakingAlgorithm alg, PageBreakPosition pbp) {
            // add footnote areas
            if (pbp.footnoteFirstListIndex < pbp.footnoteLastListIndex
                || pbp.footnoteFirstElementIndex <= pbp.footnoteLastElementIndex) {
                // call addAreas() for each FootnoteBodyLM
                for (int i = pbp.footnoteFirstListIndex; i <= pbp.footnoteLastListIndex; i++) {
                    LinkedList elementList = alg.getFootnoteList(i);
                    int firstIndex = (i == pbp.footnoteFirstListIndex 
                            ? pbp.footnoteFirstElementIndex : 0);
                    int lastIndex = (i == pbp.footnoteLastListIndex 
                            ? pbp.footnoteLastElementIndex : elementList.size() - 1);

                    FootnoteBodyLayoutManager fblm = (FootnoteBodyLayoutManager)
                            ((KnuthElement) elementList.getFirst()).getLayoutManager();
                    LayoutContext childLC = new LayoutContext(0);
                    fblm.addAreas(new KnuthPossPosIter(elementList, firstIndex, lastIndex + 1), 
                            childLC);
                }
                // set the offset from the top margin
                Footnote parentArea = (Footnote) getCurrentPV().getBodyRegion().getFootnote();
                int topOffset = (int) curPV.getBodyRegion().getBPD() - parentArea.getBPD();
                if (separatorArea != null) {
                    topOffset -= separatorArea.getBPD();
                }
                parentArea.setTop(topOffset);
                parentArea.setSeparator(separatorArea);
            }
        }
        
        protected LayoutManager getCurrentChildLM() {
            return childFLM;
        }
        
    }
    
    /**
     * Provides access to the current page.
     * @return the current PageViewport
     */
    public PageViewport getCurrentPV() {
        return curPV;
    }

    /**
     * Provides access to this object
     * @return this PageSequenceLayoutManager instance
     */
    public PageSequenceLayoutManager getPSLM() {
        return this;
    }
    
    /**
     * This returns the first PageViewport that contains an id trait
     * matching the idref argument, or null if no such PV exists.
     *
     * @param idref the idref trait needing to be resolved 
     * @return the first PageViewport that contains the ID trait
     */
    public PageViewport getFirstPVWithID(String idref) {
        List list = areaTreeHandler.getPageViewportsContainingID(idref);
        if (list != null && list.size() > 0) {
            return (PageViewport) list.get(0);
        }
        return null;
    }

    /**
     * Add an ID reference to the current page.
     * When adding areas the area adds its ID reference.
     * For the page layout manager it adds the id reference
     * with the current page to the area tree.
     *
     * @param id the ID reference to add
     */
    public void addIDToPage(String id) {
        if (id != null && id.length() > 0) {
            areaTreeHandler.associateIDWithPageViewport(id, curPV);
        }
    }

    /**
     * Identify an unresolved area (one needing an idref to be 
     * resolved, e.g. the internal-destination of an fo:basic-link)
     * for both the AreaTreeHandler and PageViewport object.
     * 
     * The AreaTreeHandler keeps a document-wide list of idref's
     * and the PV's needing them to be resolved.  It uses this to  
     * send notifications to the PV's when an id has been resolved.
     * 
     * The PageViewport keeps lists of id's needing resolving, along
     * with the child areas (page-number-citation, basic-link, etc.)
     * of the PV needing their resolution.
     *
     * @param id the ID reference to add
     * @param res the resolvable object that needs resolving
     */
    public void addUnresolvedArea(String id, Resolvable res) {
        curPV.addUnresolvedIDRef(id, res);
        areaTreeHandler.addUnresolvedIDRef(id, curPV);
    }

    /**
     * Bind the RetrieveMarker to the corresponding Marker subtree.
     * If the boundary is page then it will only check the
     * current page. For page-sequence and document it will
     * lookup preceding pages from the area tree and try to find
     * a marker.
     * If we retrieve a marker from a preceding page,
     * then the containing page does not have a qualifying area,
     * and all qualifying areas have ended.
     * Therefore we use last-ending-within-page (Constants.EN_LEWP)
     * as the position. 
     *
     * @param rm the RetrieveMarker instance whose properties are to
     * used to find the matching Marker.
     * @return a bound RetrieveMarker instance, or null if no Marker
     * could be found.
     */
    public RetrieveMarker resolveRetrieveMarker(RetrieveMarker rm) {
        AreaTreeModel areaTreeModel = areaTreeHandler.getAreaTreeModel();
        String name = rm.getRetrieveClassName();
        int pos = rm.getRetrievePosition();
        int boundary = rm.getRetrieveBoundary();               
        
        // get marker from the current markers on area tree
        Marker mark = (Marker)curPV.getMarker(name, pos);
        if (mark == null && boundary != EN_PAGE) {
            // go back over pages until mark found
            // if document boundary then keep going
            boolean doc = boundary == EN_DOCUMENT;
            int seq = areaTreeModel.getPageSequenceCount();
            int page = areaTreeModel.getPageCount(seq) - 1;
            while (page < 0 && doc && seq > 1) {
                seq--;
                page = areaTreeModel.getPageCount(seq) - 1;
            }
            while (page >= 0) {
                PageViewport pv = areaTreeModel.getPage(seq, page);
                mark = (Marker)pv.getMarker(name, Constants.EN_LEWP);
                if (mark != null) {
                    break;
                }
                page--;
                if (page < 0 && doc && seq > 1) {
                    seq--;
                    page = areaTreeModel.getPageCount(seq) - 1;
                }
            }
        }

        if (mark == null) {
            log.debug("found no marker with name: " + name);
            return null;
        } else {
            rm.bindMarker(mark);
            return rm;
        }
    }

    private PageViewport makeNewPage(boolean bIsBlank, boolean bIsLast) {
        if (curPV != null) {
            finishPage();
        }

        currentPageNum++;

        curPV = pvProvider.getPageViewport(bIsBlank,
                currentPageNum, PageViewportProvider.RELTO_PAGE_SEQUENCE);

        if (log.isDebugEnabled()) {
            log.debug("[" + curPV.getPageNumberString() + (bIsBlank ? "*" : "") + "]");
        }
        return curPV;
    }

    private void layoutSideRegion(int regionID) {
        SideRegion reg = (SideRegion)curPV.getSPM().getRegion(regionID);
        if (reg == null) {
            return;
        }
        StaticContent sc = pageSeq.getStaticContent(reg.getRegionName());
        if (sc == null) {
            return;
        }

        StaticContentLayoutManager lm = (StaticContentLayoutManager)
            getLayoutManagerMaker().makeStaticContentLayoutManager(
                    this, sc, reg);
        lm.doLayout();
    }

    private void finishPage() {
        curPV.dumpMarkers();
        // Layout side regions
        layoutSideRegion(FO_REGION_BEFORE); 
        layoutSideRegion(FO_REGION_AFTER);
        layoutSideRegion(FO_REGION_START);
        layoutSideRegion(FO_REGION_END);
        
        // Try to resolve any unresolved IDs for the current page.
        // 
        areaTreeHandler.tryIDResolution(curPV);
        // Queue for ID resolution and rendering
        areaTreeHandler.getAreaTreeModel().addPage(curPV);
        if (log.isDebugEnabled()) {
            log.debug("page finished: " + curPV.getPageNumberString() 
                    + ", current num: " + currentPageNum);
        }
        curPV = null;
    }
    
    /**
     * Depending on the kind of break condition, move to next column
     * or page. May need to make an empty page if next page would
     * not have the desired "handedness".
     * @param breakVal - value of break-before or break-after trait.
     */
    private void handleBreakTrait(int breakVal) {
        if (breakVal == Constants.EN_COLUMN) {
            if (curPV.getCurrentSpan().hasMoreFlows()) {
                curPV.getCurrentSpan().moveToNextFlow();
            } else {
                curPV = makeNewPage(false, false);
            }
            return;
        }
        log.debug("handling break-before after page " + currentPageNum 
            + " breakVal=" + breakVal);
        if (needBlankPageBeforeNew(breakVal)) {
            curPV = makeNewPage(true, false);
        }
        if (needNewPage(breakVal)) {
            curPV = makeNewPage(false, false);
        }
    }

    /**
     * Check if a blank page is needed to accomodate
     * desired even or odd page number.
     * @param breakVal - value of break-before or break-after trait.
     */
    private boolean needBlankPageBeforeNew(int breakVal) {
        if (breakVal == Constants.EN_PAGE || (curPV.getPage().isEmpty())) {
            // any page is OK or we already have an empty page
            return false;
        } else {
            /* IF we are on the kind of page we need, we'll need a new page. */
            if (currentPageNum % 2 == 0) { // even page
                return (breakVal == Constants.EN_EVEN_PAGE);
            } else { // odd page
                return (breakVal == Constants.EN_ODD_PAGE);
            }
        }
    }

    /**
     * See if need to generate a new page
     * @param breakVal - value of break-before or break-after trait.
     */
    private boolean needNewPage(int breakVal) {
        if (curPV.getPage().isEmpty()) {
            if (breakVal == Constants.EN_PAGE) {
                return false;
            } else if (currentPageNum % 2 == 0) { // even page
                return (breakVal == Constants.EN_ODD_PAGE);
            } else { // odd page
                return (breakVal == Constants.EN_EVEN_PAGE);
            }
        } else {
            return true;
        }
    }
    
    
    /**
     * <p>This class delivers PageViewport instances. It also caches them as necessary.
     * </p>
     * <p>Additional functionality makes sure that surplus instances that are requested by the
     * page breaker are properly discarded, especially in situations where hard breaks cause
     * blank pages. The reason for that: The page breaker sometimes needs to preallocate 
     * additional pages since it doesn't know exactly until the end how many pages it really needs.
     * </p>
     */
    public class PageViewportProvider {
        
        /** Indices are evaluated relative to the first page in the page-sequence. */
        public static final int RELTO_PAGE_SEQUENCE = 0;
        /** Indices are evaluated relative to the first page in the current element list. */
        public static final int RELTO_CURRENT_ELEMENT_LIST = 1;
        
        private int startPageOfPageSequence;
        private int startPageOfCurrentElementList;
        private List cachedPageViewports = new java.util.ArrayList();
        
        /**
         * Main constructor.
         * @param ps The page-sequence the provider operates on
         */
        public PageViewportProvider(PageSequence ps) {
            this.startPageOfPageSequence = ps.getStartingPageNumber();
        }
        
        /**
         * The page breaker notifies the provider about the page number an element list starts
         * on so it can later retrieve PageViewports relative to this first page.
         * @param startPage the number of the first page for the element list.
         */
        public void setStartPageOfNextElementList(int startPage) {
            log.debug("start page of the next element list is: " + startPage);
            this.startPageOfCurrentElementList = startPage;
        }
        
        /**
         * Returns a PageViewport.
         * @param bIsBlank true if this page is supposed to be blank.
         * @param index Index of the page (see relativeTo)
         * @param relativeTo Defines which value the index parameter should be evaluated relative 
         * to. (One of PageViewportProvider.RELTO_*)
         * @return the requested PageViewport
         */
        public PageViewport getPageViewport(boolean bIsBlank, int index, int relativeTo) {
            if (relativeTo == RELTO_PAGE_SEQUENCE) {
                return getPageViewport(bIsBlank, index);
            } else if (relativeTo == RELTO_CURRENT_ELEMENT_LIST) {
                int effIndex = startPageOfCurrentElementList + index;
                effIndex += startPageOfPageSequence;
                return getPageViewport(bIsBlank, effIndex);
            } else {
                throw new IllegalArgumentException(
                        "Illegal value for relativeTo: " + relativeTo);
            }
        }
        
        private PageViewport getPageViewport(boolean bIsBlank, int index) {
            if (log.isTraceEnabled()) {
                log.trace("getPageViewport(" + index + " " + bIsBlank);
            }
            int intIndex = index - startPageOfPageSequence;
            if (log.isTraceEnabled()) {
                if (bIsBlank) {
                    log.trace("blank page requested: " + index);
                }
            }
            while (intIndex >= cachedPageViewports.size()) {
                if (log.isTraceEnabled()) {
                    log.trace("Caching " + index);
                }
                cacheNextPageViewport(index, bIsBlank);
            }
            PageViewport pv = (PageViewport)cachedPageViewports.get(intIndex);
            if (pv.isBlank() != bIsBlank) {
                log.debug("blank condition doesn't match. Replacing PageViewport.");
                while (intIndex < cachedPageViewports.size()) {
                    this.cachedPageViewports.remove(cachedPageViewports.size() - 1);
                    if (!pageSeq.goToPreviousSimplePageMaster()) {
                        log.warn("goToPreviousSimplePageMaster() on the first page called!");
                    }
                }
                cacheNextPageViewport(index, bIsBlank);
            }
            return pv;
        }
        
        private void cacheNextPageViewport(int index, boolean bIsBlank) {
            try {
                String pageNumberString = pageSeq.makeFormattedPageNumber(index);
                SimplePageMaster spm = pageSeq.getNextSimplePageMaster(
                        index, (startPageOfPageSequence == index), bIsBlank);
                    
                Region body = spm.getRegion(FO_REGION_BODY);
                if (!pageSeq.getMainFlow().getFlowName().equals(body.getRegionName())) {
                    // this is fine by the XSL Rec (fo:flow's flow-name can be mapped to
                    // any region), but we don't support it yet.
                    throw new FOPException("Flow '" + pageSeq.getMainFlow().getFlowName()
                        + "' does not map to the region-body in page-master '"
                        + spm.getMasterName() + "'.  FOP presently "
                        + "does not support this.");
                }
                PageViewport pv = new PageViewport(spm, pageNumberString, bIsBlank);
                cachedPageViewports.add(pv);
            } catch (FOPException e) {
                //TODO Maybe improve. It'll mean to propagate this exception up several
                //methods calls.
                throw new IllegalStateException(e.getMessage());
            }
        }
        
    }
}
