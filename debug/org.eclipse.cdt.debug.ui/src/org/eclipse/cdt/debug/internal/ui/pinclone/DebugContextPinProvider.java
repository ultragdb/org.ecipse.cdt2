/*****************************************************************
 * Copyright (c) 2010 Texas Instruments and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Patrick Chuong (Texas Instruments) - Pin and Clone Supports (331781)
 *****************************************************************/
package org.eclipse.cdt.debug.internal.ui.pinclone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.cdt.debug.ui.IPinProvider;
import org.eclipse.cdt.debug.ui.IPinProvider.IPinElementHandle;
import org.eclipse.cdt.debug.ui.PinElementHandle;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.ui.contexts.AbstractDebugContextProvider;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.contexts.IDebugContextProvider2;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Pin debug context provider. 
 * It takes a debug context and translate to a handle for pinning purpose.
 */
public class DebugContextPinProvider extends AbstractDebugContextProvider implements IDebugContextProvider2 {
	private ISelection fActiveContext;
	private final Set<IPinElementHandle> fPinHandles;
	private final IWorkbenchPart fWorkbenchPart;
	private final Map<IPinElementHandle, IPinProvider> fPinProvider;
	
	/**
	 * Constructor.
	 * 
	 * @param part the workbench part of where the pin action takes place
	 * @param activeContext the debug context selection
	 */
	public DebugContextPinProvider(IWorkbenchPart part, ISelection activeContext) {
		super(part);
		fWorkbenchPart = part;
		fPinProvider = new HashMap<IPinElementHandle, IPinProvider>();
		
		fActiveContext = activeContext;
		fPinHandles = pin(part, activeContext);
	}
	
	/**
	 * Dispose the provider.
	 */
	public void dispose() {
		for (Entry<IPinElementHandle, IPinProvider> entry : fPinProvider.entrySet()) {
			entry.getValue().unpin(fWorkbenchPart, entry.getKey());
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextProvider2#isWindowContextProvider()
	 */
	public boolean isWindowContextProvider() {
		return false;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextProvider#getActiveContext()
	 */
	public ISelection getActiveContext() {
		return fActiveContext;
	}
	
	/**
	 * Returns the pinned debug context handles.
	 * 
	 * @return the handle set
	 */
	public Set<IPinElementHandle> getPinHandles() {
		return fPinHandles;
	}
	
	/**
	 * Returns whether the current pinned handles are pinned to the given debug context.
	 * 
	 * @param debugContext the debug context in question
	 * @return true if the pinned handles are pinned to the debug context
	 */
	public boolean isPinnedTo(Object debugContext) {
		IPinProvider pinProvider = null;
		if (debugContext instanceof IAdaptable) {
			pinProvider = (IPinProvider) ((IAdaptable)debugContext).getAdapter(IPinProvider.class);
		}		

		for (IPinElementHandle handle : fPinHandles) {
			if (pinProvider != null && pinProvider.isPinnedTo(debugContext, handle)) {
				return true;			
			
			} else if (handle.getDebugContext().equals(debugContext)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Pin the given debug context selection.
	 * 
	 * @param part the workbench part where the pin action is requested
	 * @param selection the debug context selection
	 * @return a set of pinned handle
	 */
	private Set<IPinElementHandle> pin(IWorkbenchPart part, ISelection selection) {
		Set<IPinElementHandle> handles = new HashSet<IPinElementHandle>();
		
		if (selection instanceof IStructuredSelection) {
			for (Object element : ((IStructuredSelection)selection).toList()) {
				IPinProvider pinProvider = null;
				if (element instanceof IAdaptable) {
					pinProvider = (IPinProvider) ((IAdaptable)element).getAdapter(IPinProvider.class);					
				}
				
				if (pinProvider != null) {
					IPinElementHandle handle = pinProvider.pin(fWorkbenchPart, element);
					handles.add(handle);
					fPinProvider.put(handle, pinProvider);					
				} else
					handles.add(new PinElementHandle(element, null));					
			}
		} 
		
		return handles;
	}
	
	/**
	 * Delegates debug event to the listener.
	 * 
	 * @param event debug event
	 */
	public void delegateEvent(final DebugContextEvent event) {
		fActiveContext = event.getContext();
		fire(event);
	}
}