package org.eclipse.cdt.internal.ui;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.resource.ImageRegistry;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICFile;
import org.eclipse.cdt.core.model.IBinary;
import org.eclipse.cdt.ui.*;

/**
 * The label provider for the c model elements.
 */
public class CElementLabelProvider extends LabelProvider {

	private ImageRegistry fImageRegistry;
	private WorkbenchLabelProvider fWorkbenchLabelProvider;

	private CElementImageProvider fImageLabelProvider;
	
	private IAdornmentProvider[] fAdornmentProviders;

	private int fImageFlags;
	private int fTextFlags;
	
	public CElementLabelProvider() {
		this(0 /* CElementLabels.M_PARAMETER_TYPES */, CElementImageProvider.OVERLAY_ICONS, null);
	}
	
	/**
	 * @param textFlags Flags defined in <code>JavaElementLabels</code>.
	 * @param imageFlags Flags defined in <code>JavaElementImageProvider</code>.
	 */
	public CElementLabelProvider(int textFlags, int imageFlags, IAdornmentProvider[] adormentProviders) {
		fImageRegistry= CUIPlugin.getDefault().getImageRegistry();
		fWorkbenchLabelProvider= new WorkbenchLabelProvider();
		
		fImageLabelProvider= new CElementImageProvider();
		fAdornmentProviders= adormentProviders; 
		
		fImageFlags= imageFlags;
		fTextFlags= textFlags;
	}

	/**
	 * @see ILabelProvider#getText
	 */
	public String getText(Object element) {
		if (element instanceof ICElement) {
			ICElement celem= (ICElement)element;
			
			String name= celem.getElementName();
			if (celem.getElementType() == ICElement.C_FUNCTION) {
				name += "()";
			} else if(celem.getElementType() == ICElement.C_FUNCTION_DECLARATION) {
				name += "();";
			}
			if (celem instanceof IBinary) {
				name += " - [" + ((IBinary)celem).getCPU() + "]";
			}
			return name;
		}
		return fWorkbenchLabelProvider.getText(element);
	}

	/**
	 * @see ILabelProvider#getImage
	 */
	public Image getImage(Object element) {
		int imageFlags= getImageFlags();
		if (fAdornmentProviders != null) {
			for (int i= 0; i < fAdornmentProviders.length; i++) {
				imageFlags |= fAdornmentProviders[i].computeAdornmentFlags(element);
			}
		}
		
		if(element instanceof ICElement) {
			Image result= fImageLabelProvider.getImageLabel(element, imageFlags);
			if (result != null) {
				return result;
			}
		}
		if(element instanceof ICFile) {
			//element = ((ICFile)element).getFile();
		}
		return fWorkbenchLabelProvider.getImage(element);

	}
	
	/**
	 * Gets the image key for a c element.
	 * @param The type of the element
	 */
	public static String getCElementImage(ICElement celement) {
		int type = celement.getElementType();
		switch (type) {
			case ICElement.C_CONTAINER:
				return CPluginImages.IMG_OBJS_CONTAINER;

			case ICElement.C_FILE:
				ICFile cfile = (ICFile)celement;
				if (cfile.isArchive()) {
					return CPluginImages.IMG_OBJS_ARCHIVE;
				} else if (cfile.isBinary()) {
					IBinary bin = (IBinary)cfile;
					if (bin.isExecutable()) {
						if (bin.hasDebug())
							return CPluginImages.IMG_OBJS_CEXEC_DEBUG;
						return CPluginImages.IMG_OBJS_CEXEC;
					} else if (bin.isSharedLib()) {
						return CPluginImages.IMG_OBJS_SHLIB;
					}
					return CPluginImages.IMG_OBJS_BINARY;
				} else if (cfile.isTranslationUnit()) {
					return CPluginImages.IMG_OBJS_TUNIT;
				}
				break;

			case ICElement.C_UNION:
				return CPluginImages.IMG_OBJS_UNION;
				
			case ICElement.C_CLASS:
				return CPluginImages.IMG_OBJS_CLASS;
				
			case ICElement.C_STRUCT:
				return CPluginImages.IMG_OBJS_STRUCT;

			case ICElement.C_FIELD:
			case ICElement.C_VARIABLE:
				return CPluginImages.IMG_OBJS_FIELD;

			case ICElement.C_FUNCTION:
				return CPluginImages.IMG_OBJS_FUNCTION;

			case ICElement.C_FUNCTION_DECLARATION:
				return CPluginImages.IMG_OBJS_DECLARATION;

			case ICElement.C_INCLUDE:
				return CPluginImages.IMG_OBJS_INCLUDE;

			case ICElement.C_MACRO:
				return CPluginImages.IMG_OBJS_MACRO;
		}
		return null;
	}

	/**
	 * @see IBaseLabelProvider#dispose()
	 */
	public void dispose() {
		if (fAdornmentProviders != null) {
			for (int i= 0; i < fAdornmentProviders.length; i++) {
				fAdornmentProviders[i].dispose();
			}
		}
		if (fWorkbenchLabelProvider != null) {
			fWorkbenchLabelProvider.dispose();
			fWorkbenchLabelProvider= null;
		}
		fImageRegistry= null;
		if(fImageLabelProvider != null) {
			fImageLabelProvider.dispose();
		}
	}
	
	/**
	 * Sets the textFlags.
	 * @param textFlags The textFlags to set
	 */
	public void setTextFlags(int textFlags) {
		fTextFlags= textFlags;
	}

	/**
	 * Sets the imageFlags 
	 * @param imageFlags The imageFlags to set
	 */
	public void setImageFlags(int imageFlags) {
		fImageFlags= imageFlags;
	}
	
	/**
	 * Gets the image flags.
	 * Can be overwriten by super classes.
	 * @return Returns a int
	 */
	public int getImageFlags() {
		return fImageFlags;
	}

	/**
	 * Gets the text flags. Can be overwriten by super classes.
	 * @return Returns a int
	 */
	public int getTextFlags() {
		return fTextFlags;
	}
	
	public static IAdornmentProvider[] getAdornmentProviders(boolean errortick, IAdornmentProvider extra) {
		if (errortick) {
			if (extra == null) {
				return new IAdornmentProvider[] { new ErrorTickAdornmentProvider() };
			} else {
				return new IAdornmentProvider[] { new ErrorTickAdornmentProvider(), extra };
			}
		}
		if (extra != null) {
			return new IAdornmentProvider[] { extra };
		}
		return null;
	}
}
