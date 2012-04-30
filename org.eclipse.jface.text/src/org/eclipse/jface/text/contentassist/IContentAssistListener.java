/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.text.contentassist;

import org.eclipse.swt.events.VerifyEvent;

import org.eclipse.jface.text.IEventConsumer;


/**
 * An interface whereby listeners can not only receive key events,
 * but can also consume them to prevent subsequent listeners from
 * processing the event.
 */
interface IContentAssistListener extends IEventConsumer {

	/**
	 * Verifies the key event.
	 *
	 * @param event the verify event
	 * @return <code>true</code> if processing should be continued by additional listeners
	 * @see org.eclipse.swt.custom.VerifyKeyListener#verifyKey(VerifyEvent)
	 */
	public boolean verifyKey(VerifyEvent event);
}
