/******************************************************************************
 * Product: JPiere                                                            *
 * Copyright (C) Hideaki Hagiwara (h.hagiwara@oss-erp.co.jp)                  *
 *                                                                            *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY.                          *
 * See the GNU General Public License for more details.                       *
 *                                                                            *
 * JPiere is maintained by OSS ERP Solutions Co., Ltd.                        *
 * (http://www.oss-erp.co.jp)                                                 *
 *****************************************************************************/
package jpiere.plugin.webui.window.factory;

import org.adempiere.webui.panel.ADForm;
import org.compiere.model.MQuery;

/**
 *
 * @author Hideaki Hagiwara
 *
 */
public interface IFormWindowZoomFactory {

	/**
	 *
	 * @param formName
	 * @return new form instance
	 */
	public ADForm newFormInstance(int AD_Window_ID, MQuery query);

}