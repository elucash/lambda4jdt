/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.internal.text.link.contentassist;

import org.eclipse.jface.text.contentassist.ICompletionProposal;


/**
 *
 */
public interface IProposalListener {

	/**
	 * @param proposal the completion proposal
	 */
	void proposalChosen(ICompletionProposal proposal);

}
