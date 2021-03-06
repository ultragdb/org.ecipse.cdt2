/*******************************************************************************
 * Copyright (c) 2007, 2011 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 * IBM Corporation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.buildproperties;

import org.eclipse.cdt.internal.core.SafeStringInterner;
import org.eclipse.cdt.managedbuilder.buildproperties.IBuildProperty;
import org.eclipse.cdt.managedbuilder.buildproperties.IBuildPropertyType;
import org.eclipse.cdt.managedbuilder.buildproperties.IBuildPropertyValue;
import org.eclipse.cdt.managedbuilder.core.ManagedBuilderCorePlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public class BuildProperty implements IBuildProperty{
	private IBuildPropertyType fType;
	private IBuildPropertyValue fValue;

	BuildProperty(String property) throws CoreException {
		int index = property.indexOf(BuildPropertyManager.PROPERTY_VALUE_SEPARATOR);
		String type, value;
		if(index != -1){
			type = SafeStringInterner.safeIntern(property.substring(0, index));
			value = SafeStringInterner.safeIntern(property.substring(index + 1));
		} else {
			type = SafeStringInterner.safeIntern(property);
			value = null;
		}

		fType = BuildPropertyManager.getInstance().getPropertyType(type);
		if(fType == null){
			throw new CoreException(new Status(IStatus.ERROR,
					ManagedBuilderCorePlugin.getUniqueIdentifier(),
					BuildPropertiesMessages.getString("BuildProperty.0"))); //$NON-NLS-1$
		}
		setValue(value);
	}

	BuildProperty(IBuildPropertyType type, String valueId) throws CoreException {
		fType = type;
		setValue(valueId);
	}

	@Override
	public IBuildPropertyType getPropertyType(){
		return fType;
	}

	private void setValue(String id) throws CoreException {
		IBuildPropertyValue value = fType.getSupportedValue(id);

		if(value == null)
			throw new CoreException(new Status(IStatus.ERROR,
				ManagedBuilderCorePlugin.getUniqueIdentifier(),
				BuildPropertiesMessages.getString("BuildProperty.1")+id)); //$NON-NLS-1$

		setValue(value);
	}

	private void setValue(IBuildPropertyValue value){
		fValue = value;
	}

	@Override
	public IBuildPropertyValue getValue(){
		return fValue;
	}

	@Override
	public String toString(){
		return toString(fType.toString(), fValue.toString());
	}

	public static String toString(String type, String value){
		StringBuffer buf = new StringBuffer();
		buf.append(type).append(BuildPropertyManager.PROPERTY_VALUE_SEPARATOR).append(value);
		return buf.toString();

	}

/*	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
		}
		return null;
	}
*/
}
