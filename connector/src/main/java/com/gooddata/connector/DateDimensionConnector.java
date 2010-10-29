/*
 * Copyright (c) 2009, GoodData Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice, this list of conditions and
 *        the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 *        and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *     * Neither the name of the GoodData Corporation nor the names of its contributors may be used to endorse
 *        or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.gooddata.connector;

import com.gooddata.exception.ProcessingException;
import com.gooddata.processor.CliParams;
import com.gooddata.processor.Command;
import com.gooddata.processor.ProcessingContext;
import com.gooddata.util.StringUtil;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * GoodData Google Analytics Connector
 *
 * @author zd <zd@gooddata.com>
 * @version 1.0
 */
public class DateDimensionConnector extends AbstractConnector implements Connector {

    private static Logger l = Logger.getLogger(DateDimensionConnector.class);

    //Time dimension context (e.g. created, closed etc.)
    private String name;

    /**
     * Creates a new Time Dimension Connector
     */
    protected DateDimensionConnector() {
    }

    /**
     * Creates a new Time Dimension Connector
     * @return new Time Dimension Connector
     */
    public static DateDimensionConnector createConnector() {
        return new DateDimensionConnector();
    }

    /**
     * {@inheritDoc}
     */
    public void extract(String dir) throws IOException {

    }

    /**
     * {@inheritDoc}
     */
    public String generateMaqlCreate() {
        l.debug("Generating time dimension MAQL with context "+name);
        if(name != null && name.trim().length()>0) {
            String idp = StringUtil.toIdentifier(name);
            String ts = StringUtil.toTitle(name);
            l.debug("Generated time dimension MAQL with context "+name);
            return "INCLUDE TEMPLATE \"URN:GOODDATA:DATE\" MODIFY (IDENTIFIER \""+idp+"\", TITLE \""+ts+"\");";
        }
        else {
            l.debug("Generated time dimension MAQL with no context ");
            return "INCLUDE TEMPLATE \"URN:GOODDATA:DATE\"";            
        }

    }

    /**
     * {@inheritDoc}
     */
    public boolean processCommand(Command c, CliParams cli, ProcessingContext ctx) throws ProcessingException {
        l.debug("Processing command "+c.getCommand());
        try {
            if(c.match("LoadDateDimension") || c.match("UseDateDimension")) {
                loadDateDimension(c, cli, ctx);
            }
            else {
                l.debug("No match passing the command "+c.getCommand()+" further.");
                return super.processCommand(c, cli, ctx);
            }
        }
        catch (IOException e) {
            throw new ProcessingException(e);
        }
        l.debug("Processed command "+c.getCommand());
        return true;
    }

    /**
     * Loads DateDimension data command processor
     * @param c command
     * @param p command line arguments
     * @param ctx current processing context
     * @throws IOException in case of IO issues
     */
    private void loadDateDimension(Command c, CliParams p, ProcessingContext ctx) throws IOException {
        String ct = "";
        if(c.checkParam("name"))
            ct = c.getParam( "name");
        this.name = ct;
        // sets the current connector
        ctx.setConnector(this);
        l.info("Time Dimension Connector successfully loaded (name: " + ct + ").");
    }

	/**
	 * @return the date dimension name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the date dimension name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
}