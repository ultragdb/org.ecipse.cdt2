/*******************************************************************************
 * Copyright (c) 2012 Google, Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alex Ruiz (Google) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.core.externaltool;

import static org.eclipse.cdt.codan.core.param.IProblemPreferenceDescriptor.PreferenceType.TYPE_STRING;
import static org.eclipse.cdt.codan.internal.core.externaltool.Messages.ConfigurationSettings_args_format;

import org.eclipse.cdt.codan.core.externaltool.SingleConfigurationSetting;
import org.eclipse.cdt.codan.core.param.BasicProblemPreference;
import org.eclipse.cdt.codan.core.param.IProblemPreferenceDescriptor;

/**
 * User-configurable setting that specifies the arguments to pass when invoking the external tool.
 * The arguments are stored in a single {@code String}.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ArgsSetting extends SingleConfigurationSetting<String> {
	static final String KEY = "externalToolArgs"; //$NON-NLS-1$

	/**
	 * Constructor.
	 * @param externalToolName the name of the external tool, to be displayed to the user.
	 * @param defaultValue the default value of the setting.
	 */
	public ArgsSetting(String externalToolName, String defaultValue) {
		super(newPreferenceDescriptor(externalToolName), defaultValue, String.class);
	}

	private static IProblemPreferenceDescriptor newPreferenceDescriptor(String externalToolName) {
		String label = String.format(ConfigurationSettings_args_format, externalToolName);
		return new BasicProblemPreference(KEY, label, TYPE_STRING);
	}
}