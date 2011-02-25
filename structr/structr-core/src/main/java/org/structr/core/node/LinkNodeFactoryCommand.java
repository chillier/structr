/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.structr.core.node;

import org.structr.core.entity.Link;
import org.structr.core.entity.AbstractNode;

/**
 *
 * @author cmorgner
 */
public class LinkNodeFactoryCommand extends NodeFactoryCommand
{
	@Override
	public Object execute(Object... parameters)
	{
                AbstractNode sNode = (AbstractNode) super.execute(parameters);

                Link linkNode = new Link();
                linkNode.init(sNode);

		return linkNode;
	}
}
