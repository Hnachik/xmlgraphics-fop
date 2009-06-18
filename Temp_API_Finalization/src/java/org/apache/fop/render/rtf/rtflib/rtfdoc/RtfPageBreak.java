/*
 * Copyright 1999-2004 The Apache Software Foundation.
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


/*
 * This file is part of the RTF library of the FOP project, which was originally
 * created by Bertrand Delacretaz <bdelacretaz@codeconsult.ch> and by other
 * contributors to the jfor project (www.jfor.org), who agreed to donate jfor to
 * the FOP project.
 */

package org.apache.fop.render.rtf.rtflib.rtfdoc;

import java.io.Writer;
import java.io.IOException;

/**  "Model" of an RTF page break
 *  @author Bertrand Delacretaz bdelacretaz@codeconsult.ch
 */

public class RtfPageBreak extends RtfElement {
    /** Create an RTF paragraph as a child of given container with default attributes */
    RtfPageBreak(IRtfPageBreakContainer parent, Writer w) throws IOException {
        super((RtfContainer)parent, w);
    }

    /**
     * Overridden to write our attributes before our content
     * @throws IOException for I/O problems
     */
    protected void writeRtfContent() throws IOException {
        writeControlWord("page");
    }

    /**
     * @return true if this element would generate no "useful" RTF content
     */
    public boolean isEmpty() {
        return false;
    }
}