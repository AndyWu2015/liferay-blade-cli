/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.blade.eclipse.provider;

import com.liferay.blade.api.CUCache;
import com.liferay.blade.api.MigrationConstants;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jst.jsp.core.internal.java.JSPTranslator;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.osgi.service.component.annotations.Component;

@Component(
	property = {
		"type=jsp"
	},
	service = CUCache.class
)
public class CUCacheWTP implements CUCache<JSPTranslationPrime> {

	private static final Map<File, WeakReference<JSPTranslationPrime>> _map = new WeakHashMap<>();

	@Override
	public JSPTranslationPrime getCU(File file, char[] javavSource) {
		try {
			synchronized (_map) {
				WeakReference<JSPTranslationPrime> translationRef = _map.get(file);

				if (translationRef == null || translationRef.get() == null) {
					final JSPTranslationPrime newTranslation = createJSPTranslation(file);

					_map.put(file, new WeakReference<JSPTranslationPrime>(newTranslation));

					return newTranslation;
				}
				else {
					return translationRef.get();
				}
			}
		}
		catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public void unget(File file) {
		synchronized (_map) {
			_map.remove(file);
		}
	}

	private JSPTranslationPrime createJSPTranslation(File file) {
		IDOMModel jspModel = null;

		try {
			// try to find the file in the current workspace, if it can't find
			// it then fall back to copy

			final IFile jspFile = getIFile(file);

			jspModel = (IDOMModel) StructuredModelManager.getModelManager().getModelForRead(jspFile);
			final IDOMDocument domDocument = jspModel.getDocument();
			final IDOMNode domNode = (IDOMNode) domDocument.getDocumentElement();

			final IProgressMonitor npm = new NullProgressMonitor();
			final JSPTranslator translator = new JSPTranslatorPrime();

			if (domNode != null) {
				translator.reset((IDOMNode) domDocument.getDocumentElement(), npm);
			} else {
				translator.reset((IDOMNode) domDocument.getFirstChild(), npm);
			}

			translator.translate();

			final IJavaProject javaProject = JavaCore.create(jspFile.getProject());

			return new JSPTranslationPrime(javaProject, translator, jspFile);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (jspModel != null) {
				jspModel.releaseFromRead();
			}
		}

		return null;
	}

	public IFile getIFile(File file) {
		IFile retval = null;

		// first try to find this file in the current workspace
		final IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(file.toURI());

		// if there are multiple files in this workspace use the shortest path
		if (files != null && files.length == 1) {
			retval = files[0];
		}
		else if (files != null && files.length > 0) {
			for (IFile ifile : files) {
				if (retval == null) {
					retval = ifile;
				}
				else {
					// prefer the path that is shortest (to avoid a nested
					// version)
					if (ifile.getFullPath().segmentCount() < retval.getFullPath().segmentCount()) {
						retval = ifile;
					}
				}
			}
		}

		if (retval == null) {
			try {
				retval = new WorkspaceHelper().createIFile(MigrationConstants.HELPER_PROJECT_NAME, file);
			}
			catch (CoreException | IOException e) {
				e.printStackTrace();
			}
		}

		return retval;
	}

}
