/*******************************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corp. - Rational Software - initial implementation
 ******************************************************************************/

package org.eclipse.cdt.internal.core.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import org.eclipse.cdt.core.parser.IScannerContext;
import org.eclipse.cdt.core.parser.ISourceElementRequestor;
import org.eclipse.cdt.core.parser.ScannerException;
import org.eclipse.cdt.core.parser.ast.IASTInclusion;

/**
 * @author aniefer
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ContextStack {

	public ContextStack(){
		super();
	}

    public void updateContext(Reader reader, String filename, int type, IASTInclusion inclusion, ISourceElementRequestor requestor) throws ScannerException {
        updateContext(reader, filename, type, inclusion, requestor, -1, -1);
    }
  
	public void updateContext(Reader reader, String filename, int type, IASTInclusion inclusion, ISourceElementRequestor requestor, int macroOffset, int macroLength) throws ScannerException 
    {
        // If we expand a macro within a macro, then keep offsets of the top-level one,
        // as only the top level macro identifier is properly positioned    
        if (type == IScannerContext.MACROEXPANSION) {
            if (currentContext.getKind() == IScannerContext.MACROEXPANSION) {
                macroOffset = currentContext.getMacroOffset();
                macroLength = currentContext.getMacroLength();
            }
        }
        
		undoStack.clear();
		push( new ScannerContext().initialize(reader, filename, type, null, macroOffset, macroLength ), requestor );	
	}
	
	protected void push( IScannerContext context, ISourceElementRequestor requestor ) throws ScannerException
	{
		if( context.getKind() == IScannerContext.INCLUSION )
		{
			if( !inclusions.add( context.getFilename() ) )
				throw new ScannerException( "Inclusion " + context.getFilename() + " already encountered." );
			if( requestor != null )
				requestor.enterInclusion( context.getExtension() );

		} else if( context.getKind() == IScannerContext.MACROEXPANSION )
		{
			if( !defines.add( context.getFilename() ) )
				throw new ScannerException( "Define " + context.getFilename() + " already encountered." );
		}
		if( currentContext != null )
			contextStack.push(currentContext);
		
		currentContext = context;
		if( context.getKind() == IScannerContext.TOP )
			topContext = context;
	}
	
	public boolean rollbackContext(ISourceElementRequestor requestor) {
		try {
			currentContext.getReader().close();
		} catch (IOException ie) {
			System.out.println("Error closing reader");
		}

		if( currentContext.getKind() == IScannerContext.INCLUSION )
		{
			inclusions.remove( currentContext.getFilename() );
			if( requestor != null )
				requestor.exitInclusion( currentContext.getExtension() );
		} else if( currentContext.getKind() == IScannerContext.MACROEXPANSION )
		{
			defines.remove( currentContext.getFilename() );
		}
		
		undoStack.addFirst( currentContext );
		
		if (contextStack.isEmpty()) {
			currentContext = null;
			return false;
		}

		currentContext = (ScannerContext) contextStack.pop();
		return true;
	}
	
	public void undoRollback( IScannerContext undoTo, ISourceElementRequestor requestor ) throws ScannerException {
		if( currentContext == undoTo ){
			return;
		}
		
		int size = undoStack.size();
		if( size > 0 )
		{
			Iterator iter = undoStack.iterator();
			for( int i = size; i > 0; i-- )
			{
				push( (IScannerContext) undoStack.removeFirst(), requestor );
				
				if( currentContext == undoTo )
					break;
			}	
		}
	}
	
	/**
	 * 
	 * @param symbol
	 * @return boolean, whether or not we should expand this definition
	 * 
	 * 16.3.4-2 If the name of the macro being replaced is found during 
	 * this scan of the replacement list it is not replaced.  Further, if 
	 * any nested replacements encounter the name of the macro being replaced,
	 * it is not replaced. 
	 */
	protected boolean shouldExpandDefinition( String symbol )
	{
		return !defines.contains( symbol );
	}
	
	public IScannerContext getCurrentContext(){
		return currentContext;
	}
	
	private IScannerContext currentContext, topContext;
	private Stack contextStack = new Stack();
	private LinkedList undoStack = new LinkedList();
	private Set inclusions = new HashSet(); 
	private Set defines = new HashSet();
	
	/**
	 * @return
	 */
	public IScannerContext getTopContext() {
		return topContext;
	}
	
}
