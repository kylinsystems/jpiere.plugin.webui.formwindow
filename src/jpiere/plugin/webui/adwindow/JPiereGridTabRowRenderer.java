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

package jpiere.plugin.webui.adwindow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.adempiere.util.GridRowCtx;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.adwindow.ADWindow;					//JPIERE-0014
import org.adempiere.webui.adwindow.AbstractADWindowContent;	//JPIERE-0014
import org.adempiere.webui.adwindow.DetailPane;					//JPIERE-0014
import org.adempiere.webui.adwindow.GridTableListModel;			//JPIERE-0014
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Checkbox;
import org.adempiere.webui.component.EditorBox;
import org.adempiere.webui.component.NumberBox;
import org.adempiere.webui.component.Textbox;
import org.adempiere.webui.component.Urlbox;
import org.adempiere.webui.editor.WButtonEditor;
import org.adempiere.webui.editor.WEditor;
import org.adempiere.webui.editor.WEditorPopupMenu;
import org.adempiere.webui.editor.WImageEditor;
import org.adempiere.webui.editor.WebEditorFactory;
import org.adempiere.webui.event.ActionEvent;
import org.adempiere.webui.event.ActionListener;
import org.adempiere.webui.event.ContextMenuListener;
import org.adempiere.webui.panel.HelpController;
import org.adempiere.webui.session.SessionManager;
import org.adempiere.webui.theme.ThemeManager;
import org.adempiere.webui.util.GridTabDataBinder;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MStyle;
import org.compiere.model.MSysConfig;
import org.compiere.model.X_AD_StyleLine;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Evaluator;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.zkoss.zk.au.out.AuScript;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Cell;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Paging;
import org.zkoss.zul.RendererCtrl;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;
import org.zkoss.zul.RowRendererExt;
import org.zkoss.zul.Vbox;
import org.zkoss.zul.impl.XulElement;

/**
 * Row renderer for GridTab grid.
 * @author hengsin
 *
 * @author Teo Sarca, teo.sarca@gmail.com
 * 		<li>BF [ 2996608 ] GridPanel is not displaying time
 * 			https://sourceforge.net/tracker/?func=detail&aid=2996608&group_id=176962&atid=955896
 *
 * @author Hideaki Hagiwara（h.hagiwara@oss-erp.co.jp）
 *
 */
public class JPiereGridTabRowRenderer implements RowRenderer<Object[]>, RowRendererExt, RendererCtrl, EventListener<Event> {

	public static final String GRID_ROW_INDEX_ATTR = "grid.row.index";
	private static final String CELL_DIV_STYLE = "height: 100%; cursor: pointer; ";
	private static final String CELL_DIV_STYLE_ALIGN_CENTER = CELL_DIV_STYLE + "text-align:center; ";
	private static final String CELL_DIV_STYLE_ALIGN_RIGHT = CELL_DIV_STYLE + "text-align:right; ";
	private static final String CELL_BORDER_STYLE_DEFAULT = "border: dotted 1px #dddddd;";
	private static final String CELL_BORDER_STYLE_NONE = "border: none;";
	private static final String ROW_STYLE = "border: solid 1px #dddddd; cursor:pointer";

	private static final int MAX_TEXT_LENGTH_DEFAULT = 60;
	private GridTab gridTab;
	private int windowNo;
	private GridTabDataBinder dataBinder;
	private Map<GridField, WEditor> editors = new LinkedHashMap<GridField, WEditor>();
	private Map<GridField, WEditor> readOnlyEditors = new LinkedHashMap<GridField, WEditor>();
	private Paging paging;

	private RowListener rowListener;

	private Grid grid = null;
	private JPiereGridView gridPanel = null;
	private Row currentRow;
	private Object[] currentValues;
	private boolean editing = false;
	private int currentRowIndex = -1;
	private JPiereAbstractADWindowContent m_windowPanel;
	private ActionListener buttonListener;
	/**
	 * Flag detect this view has customized column or not
	 * value is set at {@link #render(Row, Object[], int)}
	 */
	private boolean isGridViewCustomized = false;
	/** DefaultFocusField		*/
	private WEditor	defaultFocusField = null;

	/**
	 *
	 * @param gridTab
	 * @param windowNo
	 */
	public JPiereGridTabRowRenderer(GridTab gridTab, int windowNo) {
		this.gridTab = gridTab;
		this.windowNo = windowNo;
		this.dataBinder = new GridTabDataBinder(gridTab);
	}

	private WEditor getEditorCell(GridField gridField) {
		WEditor editor = editors.get(gridField);
		if (editor != null)  {
			prepareFieldEditor(gridField, editor);
			editor.addValueChangeListener(dataBinder);
			gridField.removePropertyChangeListener(editor);
			gridField.addPropertyChangeListener(editor);
			editor.setValue(gridField.getValue());
		}
		return editor;
	}

	private void prepareFieldEditor(GridField gridField, WEditor editor) {
			if (editor instanceof WButtonEditor)
            {
				if (buttonListener != null)
				{
					((WButtonEditor)editor).addActionListener(buttonListener);
				}
				else
				{
					Object window = SessionManager.getAppDesktop().findWindow(windowNo);
	            	if (window != null && window instanceof ADWindow)
	            	{
	            		AbstractADWindowContent windowPanel = ((ADWindow)window).getADWindowContent();
	            		((WButtonEditor)editor).addActionListener(windowPanel);
	            	}
				}
            }

            //streach component to fill grid cell
			if (editor.getComponent() instanceof HtmlBasedComponent) {
            	editor.fillHorizontal();
		}
	}

	public int getColumnIndex(GridField field) {
		GridField[] fields = gridPanel.getFields();
		for(int i = 0; i < fields.length; i++) {
			if (fields[i] == field)
				return i;
		}
		return 0;
	}

	private Component createReadonlyCheckbox(Object value) {
		Checkbox checkBox = new Checkbox();
		if (value != null && "true".equalsIgnoreCase(value.toString()))
			checkBox.setChecked(true);
		else
			checkBox.setChecked(false);
		checkBox.setDisabled(true);
		return checkBox;
	}

	private Component createInvisibleComponent() {
		Textbox textBox = new Textbox();
		textBox.setDisabled(true);
		textBox.setVisible(false);
		return textBox;
	}

	/**
	 * call {@link #getDisplayText(Object, GridField, int, boolean)} with isForceGetValue = false
	 * @param value
	 * @param gridField
	 * @param rowIndex
	 * @return
	 */
	private String getDisplayText(Object value, GridField gridField, int rowIndex){
		return getDisplayText(value, gridField, rowIndex, false);
	}

	/**
	 * Get display text of a field. when field have isDisplay = false always return empty string, except isForceGetValue = true
	 * @param value
	 * @param gridField
	 * @param rowIndex
	 * @param isForceGetValue
	 * @return
	 */
	private String getDisplayText(Object value, GridField gridField, int rowIndex, boolean isForceGetValue)
	{
		if (value == null)
			return "";

		if (rowIndex >= 0) {
			GridRowCtx gridRowCtx = new GridRowCtx(Env.getCtx(), gridTab, rowIndex);
			if (!isForceGetValue && !gridField.isDisplayed(gridRowCtx, true)) {
				return "";
			}
		}

		if (gridField.isEncryptedField())
		{
			return "********";
		}
		else if (readOnlyEditors.get(gridField) != null)
		{
			WEditor editor = readOnlyEditors.get(gridField);
			return editor.getDisplayTextForGridView(value);
		}
    	else
    		return value.toString();
	}

	/**
	 * get component to display value of a field.
	 * when display is boolean or button, return correspond component
	 * other return a label with text get from {@link #getDisplayText(Object, GridField, int, boolean)}
	 * @param rowIndex
	 * @param value
	 * @param gridField
	 * @param isForceGetValue
	 * @return
	 */
	private Component getDisplayComponent(int rowIndex, Object value, GridField gridField, boolean isForceGetValue) {
		Component component;
		if (gridField.getDisplayType() == DisplayType.YesNo) {
			component = createReadonlyCheckbox(value);
		} else if (gridField.isHeading()) {
			component = createInvisibleComponent();
		} else if (gridField.getDisplayType() == DisplayType.Button) {
			GridRowCtx gridRowCtx = new GridRowCtx(Env.getCtx(), gridTab, rowIndex);
			WButtonEditor editor = new WButtonEditor(gridField, rowIndex);
			editor.setValue(gridTab.getValue(rowIndex, gridField.getColumnName()));
			editor.setReadWrite(gridField.isEditable(gridRowCtx, true,true));
			editor.getComponent().setAttribute(GRID_ROW_INDEX_ATTR, rowIndex);
			editor.addActionListener(buttonListener);
			component = editor.getComponent();
		} else {
			String text = getDisplayText(value, gridField, rowIndex, isForceGetValue);
			WEditor editor = getEditorCell(gridField);
			if (editor.getDisplayComponent() == null){
				Label label = new Label();
				setLabelText(text, label);
				component = label;
			}else{
				component = editor.getDisplayComponent();
				if (component instanceof Html){
					((Html)component).setContent(text);
				}else{
					throw new UnsupportedOperationException("neet a componet has setvalue function");
				}
			}
		}
		applyFieldStyle(gridField, rowIndex, (HtmlBasedComponent) component);
		return component;
	}

	private void applyFieldStyle(GridField gridField, int rowIndex,
			HtmlBasedComponent component) {
		int AD_Style_ID = gridField.getAD_FieldStyle_ID();
		if (AD_Style_ID <= 0)
			return;
		
		GridRowCtx gridRowCtx = new GridRowCtx(Env.getCtx(), gridTab, rowIndex);
		MStyle style = MStyle.get(Env.getCtx(), AD_Style_ID);
		X_AD_StyleLine[] lines = style.getStyleLines();
		StringBuilder styleBuilder = new StringBuilder();
		for (X_AD_StyleLine line : lines) 
		{
			String inlineStyle = line.getInlineStyle().trim();
			String displayLogic = line.getDisplayLogic();
			String theme = line.getTheme();
			if (!Util.isEmpty(theme)) {
				if (!ThemeManager.getTheme().equals(theme))
					continue;
			}
			if (!Util.isEmpty(displayLogic))
			{
				if (!Evaluator.evaluateLogic(gridRowCtx, displayLogic)) 
					continue;
			}
			if (styleBuilder.length() > 0 && !(styleBuilder.charAt(styleBuilder.length()-1)==';'))
				styleBuilder.append("; ");
			styleBuilder.append(inlineStyle);
		}
		component.setStyle(styleBuilder.toString());
	}

	/**
	 * @param text
	 * @param label
	 */
	private void setLabelText(String text, Label label) {
		String display = text;
		final int MAX_TEXT_LENGTH = MSysConfig.getIntValue(MSysConfig.MAX_TEXT_LENGTH_ON_GRID_VIEW,MAX_TEXT_LENGTH_DEFAULT,Env.getAD_Client_ID(Env.getCtx()));
		if (text != null && text.length() > MAX_TEXT_LENGTH)
			display = text.substring(0, MAX_TEXT_LENGTH - 3) + "...";
		// since 5.0.8, the org.zkoss.zhtml.Text is encoded by default
//		if (display != null)
//			display = XMLs.encodeText(display);
		label.setValue(display);
		if (text != null && text.length() > MAX_TEXT_LENGTH)
			label.setTooltiptext(text);
	}

	/**
	 *
	 * @return active editor list
	 */
	public List<WEditor> getEditors() {
		List<WEditor> editorList = new ArrayList<WEditor>();
		if (!editors.isEmpty())
			editorList.addAll(editors.values());

		return editorList;
	}

	/**
	 * @param paging
	 */
	public void setPaging(Paging paging) {
		this.paging = paging;
	}

	/**
	 * Detach all editor and optionally set the current value of the editor as cell label.
	 * @param updateCellLabel
	 */
	public void stopEditing(boolean updateCellLabel) {
		if (!editing) {
			return;
		} else {
			editing = false;
		}
//		Row row = null;
		for (Entry<GridField, WEditor> entry : editors.entrySet()) {
			if (entry.getValue().getComponent().getParent() != null) {
				Component child = entry.getValue().getComponent();
				Cell div = null;
				while (div == null && child != null) {
					Component parent = child.getParent();
					if (parent instanceof Cell && parent.getParent().getParent() instanceof Row)
						div = (Cell)parent;
					else
						child = parent;
				}
				Component component = div!=null ? (Component) div.getAttribute("display.component") : null;
				if (updateCellLabel) {
					if (component instanceof Label) {
						Label label = (Label)component;
						label.getChildren().clear();
						String text = getDisplayText(entry.getValue().getValue(), entry.getValue().getGridField(), -1);
						setLabelText(text, label);
					} else if (component instanceof Checkbox) {
						Checkbox checkBox = (Checkbox)component;
						Object value = entry.getValue().getValue();
						if (value != null && "true".equalsIgnoreCase(value.toString()))
							checkBox.setChecked(true);
						else
							checkBox.setChecked(false);
					} else if (component instanceof Html){
						((Html)component).setContent(getDisplayText(entry.getValue().getValue(), entry.getValue().getGridField(), -1));
					}
				}
//				if (row == null)
//					row = (Row)(div.getParent().getParent());

				entry.getValue().getComponent().detach();
				entry.getKey().removePropertyChangeListener(entry.getValue());
				entry.getValue().removeValuechangeListener(dataBinder);

				if (component.getParent() == null || component.getParent() != div)
					div.appendChild(component);
				else if (!component.isVisible()) {
					component.setVisible(true);
				}
			}
		}

		GridTableListModel model = (GridTableListModel) grid.getModel();
		model.setEditing(false);
	}

	/**
	 * @param row
	 * @param data
	 * @see RowRenderer#render(Row, Object)
	 */
	@Override
	public void render(Row row, Object[] data, int index) throws Exception {

		//don't render if not visible
		int columnCount = 0;
		GridField[] gridPanelFields = null;
		GridField[] gridTabFields = null;
		isGridViewCustomized = false;

		if (gridPanel != null) {
			if (!gridPanel.isVisible()) {
				return;
			}
			else{
				gridPanelFields = gridPanel.getFields();
				columnCount = gridPanelFields.length;
				gridTabFields = gridTab.getFields();
				isGridViewCustomized = gridTabFields.length != gridPanelFields.length;
			}
		}

		if (grid == null)
			grid = (Grid) row.getParent().getParent();

		if (rowListener == null)
			rowListener = new RowListener((Grid)row.getParent().getParent());

		if (!isGridViewCustomized) {
			for(int i = 0; i < gridTabFields.length; i++) {
				if (gridPanelFields[i].getAD_Field_ID() != gridTabFields[i].getAD_Field_ID()) {
					isGridViewCustomized = true;
					break;
				}
			}
		}
		if (!isGridViewCustomized) {
			currentValues = data;
		} else {
			List<Object> dataList = new ArrayList<Object>();
			for(GridField gridField : gridPanelFields) {
				for(int i = 0; i < gridTabFields.length; i++) {
					if (gridField.getAD_Field_ID() == gridTabFields[i].getAD_Field_ID()) {
						dataList.add(data[i]);
						break;
					}
				}
			}
			currentValues = dataList.toArray(new Object[0]);
		}


//		Grid grid = (Grid) row.getParent().getParent();

		int rowIndex = index;
		if (paging != null && paging.getPageSize() > 0) {
			rowIndex = (paging.getActivePage() * paging.getPageSize()) + rowIndex;
		}

		Cell cell = new Cell();
		cell.setWidth("28px");
		cell.setTooltiptext(Msg.getMsg(Env.getCtx(), "Select"));
		Checkbox selection = new Checkbox();
		selection.setAttribute(GRID_ROW_INDEX_ATTR, rowIndex);
		selection.setChecked(gridTab.isSelected(rowIndex));
		cell.setStyle("background-color: transparent !important;");
		selection.addEventListener(Events.ON_CHECK, this);

		if (!selection.isChecked()) {
			if (gridPanel.selectAll.isChecked()) {
				gridPanel.selectAll.setChecked(false);
			}
		}

		cell.appendChild(selection);
		row.appendChild(cell);

		cell = new Cell();
		cell.setWidth("18px");
		cell.addEventListener(Events.ON_CLICK, this);
		cell.setTooltiptext(Util.cleanAmp(Msg.getMsg(Env.getCtx(), "EditRecord")));
		if (ThemeManager.isUseFontIconForImage()) {
			Label indicatorLabel = new Label();
			cell.appendChild(indicatorLabel);
			final Cell finalCell = cell;
			indicatorLabel.addEventListener(Events.ON_CLICK, evt->Events.postEvent(Events.ON_CLICK, finalCell, indicatorLabel.getSclass()));
		}
		cell.setValign("middle");

		row.appendChild(cell);

		Boolean isActive = null;
		Vbox vbox = null;
		int auxheadSize = gridPanel.getAuxheadSize();//JPIERE-0014:Form Window


		int sameLineColumnCounter = 0;
		for (int i = 0; i < columnCount; i++) {
			if (editors.get(gridPanelFields[i]) == null) {
				WEditor editor = WebEditorFactory.getEditor(gridPanelFields[i], true);
				if (editor != null) {
					editors.put(gridPanelFields[i], editor);
					if (editor instanceof WButtonEditor) {
						((WButtonEditor)editor).addActionListener(buttonListener);
					}

					//readonly for display text
					WEditor readOnlyEditor = WebEditorFactory.getEditor(gridPanelFields[i], true);
					if (readOnlyEditor != null) {
						readOnlyEditor.setReadWrite(false);
						readOnlyEditors.put(gridPanelFields[i], readOnlyEditor);
					}
					editor.getComponent().setWidgetOverride("fieldHeader", HelpController.escapeJavascriptContent(gridPanelFields[i].getHeader()));
	    			editor.getComponent().setWidgetOverride("fieldDescription", HelpController.escapeJavascriptContent(gridPanelFields[i].getDescription()));
	    			editor.getComponent().setWidgetOverride("fieldHelp", HelpController.escapeJavascriptContent(gridPanelFields[i].getHelp()));
	    			editor.getComponent().setWidgetListener("onFocus", "zWatch.fire('onFieldTooltip', this, null, this.fieldHeader(), this.fieldDescription(), this.fieldHelp());");

	    			//	Default Focus
	    			if (defaultFocusField == null && gridPanelFields[i].isDefaultFocus())
	    				defaultFocusField = editor;
				}
			}

			if ("IsActive".equals(gridPanelFields[i].getColumnName())) {
				isActive = Boolean.FALSE;
				if (currentValues[i] != null) {
					if ("true".equalsIgnoreCase(currentValues[i].toString())) {
						isActive = Boolean.TRUE;
					}
				}
			}

			if (!gridPanelFields[i].isDisplayedGrid() || gridPanelFields[i].isToolbarOnlyButton()) {
				continue;
			}

			Cell div = new Cell();
			String divStyle = CELL_DIV_STYLE + CELL_BORDER_STYLE_DEFAULT ;

			Component component = getDisplayComponent(rowIndex, currentValues[i], gridPanelFields[i],isGridViewCustomized);
			div.appendChild(component);
			div.setAttribute("display.component", component);

			if (DisplayType.YesNo == gridPanelFields[i].getDisplayType() || DisplayType.Image == gridPanelFields[i].getDisplayType()) {
				divStyle = CELL_DIV_STYLE_ALIGN_CENTER + CELL_BORDER_STYLE_NONE;
			}
			else if (DisplayType.isNumeric(gridPanelFields[i].getDisplayType())) {
				divStyle = CELL_DIV_STYLE_ALIGN_RIGHT + CELL_BORDER_STYLE_DEFAULT ;
			}else if( DisplayType.Button == gridPanelFields[i].getDisplayType()){
				divStyle = CELL_DIV_STYLE + CELL_BORDER_STYLE_NONE ;
			}

			GridRowCtx ctx = new GridRowCtx(Env.getCtx(), gridTab, rowIndex);
			component.setVisible(gridPanelFields[i].isDisplayed(ctx, true));

			div.setStyle(divStyle);
			ZKUpdateUtil.setWidth(div, "100%");
			div.setHeight("28px");//JPIERE-0014:Fomr Window - Set Small Height for multi-column
			div.setAttribute("columnName", gridPanelFields[i].getColumnName());
			div.addEventListener(Events.ON_CLICK, rowListener);

			//JPIERE-0014: Multi-Column Display Logic - start
			if(!gridPanelFields[i].isSameLine() || sameLineColumnCounter== 0){//First Column row

				vbox = new Vbox();
				vbox.setWidth("100%");
				vbox.addEventListener(Events.ON_CLICK, rowListener);
				vbox.appendChild(div);
				row.appendChild(vbox);
				sameLineColumnCounter = 1;

			}else{												//Over Second Column row

				if(sameLineColumnCounter <= auxheadSize){	//追加タイトル行数以下の表示カラム数の場合
					vbox.appendChild(div);
					row.appendChild(vbox);
					sameLineColumnCounter++;

				}else{									//追加タイトル行を超える場合は、次のカラムを追加する。
					vbox = new Vbox();
					vbox.setWidth("100%");
					vbox.addEventListener(Events.ON_CLICK, rowListener);
					vbox.appendChild(div);
					row.appendChild(vbox);
					sameLineColumnCounter = 1;
				}

			}//JPIERE-0014: Multi-Column Display Logic - finish

		}

		if (rowIndex == gridTab.getCurrentRow()) {
			setCurrentRow(row);
		}

		row.setStyle(ROW_STYLE);
		row.addEventListener(Events.ON_CLICK, rowListener);
		row.addEventListener(Events.ON_OK, rowListener);
		row.setTooltiptext("Row " + (rowIndex+1));

		if (isActive == null) {
			Object isActiveValue = gridTab.getValue(rowIndex, "IsActive");
			if (isActiveValue != null) {
				if ("true".equalsIgnoreCase(isActiveValue.toString())) {
					isActive = Boolean.TRUE;
				} else {
					isActive = Boolean.FALSE;
				}
			}
		}
		if (isActive != null && !isActive.booleanValue()) {
			LayoutUtils.addSclass("grid-inactive-row", row);
		}
	}

	/**
	 * @param row
	 */
	public void setCurrentRow(Row row) {
		if (currentRow != null && currentRow.getParent() != null && currentRow != row) {
			Cell cell = (Cell) currentRow.getChildren().get(1);
			if (cell != null) {
				cell.setStyle("background-color: transparent");
				cell.setSclass("row-indicator");
				if (cell.getFirstChild() != null)
					((Label)cell.getFirstChild()).setSclass("");
			}
		}
		currentRow = row;
		Cell cell = (Cell) currentRow.getChildren().get(1);
		if (cell != null) {
			if (ThemeManager.isUseFontIconForImage()) 
			{
				Label indicatorLabel = (Label) cell.getFirstChild();
				indicatorLabel.setSclass("row-indicator-selected z-icon-Edit");
			}
			else
				cell.setSclass("row-indicator-selected");
		}
		currentRowIndex = gridTab.getCurrentRow();

		if (currentRowIndex == gridTab.getCurrentRow()) {
			if (editing) {
				stopEditing(false);
				editCurrentRow();
			}
		} else {
			currentRowIndex = gridTab.getCurrentRow();
			if (editing) {
				stopEditing(false);
			}
		}

		String script = "jq('#"+row.getUuid()+"').addClass('highlight').siblings().removeClass('highlight')";

		Boolean isActive = null;
		Object isActiveValue = gridTab.getValue(currentRowIndex, "IsActive");
		if (isActiveValue != null) {
			if ("true".equalsIgnoreCase(isActiveValue.toString())) {
				isActive = Boolean.TRUE;
			} else {
				isActive = Boolean.FALSE;
			}
		}
		if (isActive != null && !isActive.booleanValue()) {
			script = "jq('#"+row.getUuid()+"').addClass('grid-inactive-row').siblings().removeClass('highlight')";
		}

		Clients.response(new AuScript(script));
	}

	/**
	 * @return Row
	 */
	public Row getCurrentRow() {
		return currentRow;
	}

	/**
	 * @return current row index ( absolute )
	 */
	public int getCurrentRowIndex() {
		return currentRowIndex;
	}

	/**
	 * Enter edit mode
	 */
	public void editCurrentRow() {
		if (currentRow != null && currentRow.getParent() != null && currentRow.isVisible()
			&& grid != null && grid.isVisible() && grid.getParent() != null && grid.getParent().isVisible()) {
			GridField[] gridPanelFields = gridPanel.getFields();
			int columnCount = gridPanelFields.length;
//			org.zkoss.zul.Columns columns = grid.getColumns();

			//skip selection and indicator column
			int colIndex = 1;

			//JPIERE-0014: Multi-Column Display Logic - start
			int auxheadSize = gridPanel.getAuxheadSize();
			int sameLineColumnCounter = 0;
			int cellCounter = 0;

			for (int i = 0; i < columnCount; i++) {
				if (!gridPanelFields[i].isDisplayedGrid() || gridPanelFields[i].isToolbarButton()) {
					continue;
				}
				colIndex ++;

				//JPIERE-0014: Multi-Column Display Logic - start
				if(!gridPanelFields[i].isSameLine() || sameLineColumnCounter== 0){//1段目の処理
					sameLineColumnCounter =1;
					cellCounter = 0;
				}else{
					if(sameLineColumnCounter <= auxheadSize){	//追加タイトル行以下のカラム数の場合
						sameLineColumnCounter++;
						cellCounter++;
						colIndex--;
					}else{//追加タイトル行を超える場合は、次のカラムを追加する。
						sameLineColumnCounter =1;
						cellCounter = 0;
					}
				}


				if (editors.get(gridPanelFields[i]) == null)
					editors.put(gridPanelFields[i], WebEditorFactory.getEditor(gridPanelFields[i], true));

				Vbox vbox = (Vbox) currentRow.getChildren().get(colIndex);

				Cell div = (Cell)vbox.getChildren().get(cellCounter);
				WEditor editor = getEditorCell(gridPanelFields[i]);
				if (div.getChildren().isEmpty() || !(div.getChildren().get(0) instanceof Button))
					div.getChildren().clear();
				else if (!div.getChildren().isEmpty()) {
					div.getChildren().get(0).setVisible(false);
				}
				div.appendChild(editor.getComponent());
				WEditorPopupMenu popupMenu = editor.getPopupMenu();

	            if (popupMenu != null)
	            {
	            	popupMenu.addMenuListener((ContextMenuListener)editor);
	            	div.appendChild(popupMenu);
	            	popupMenu.addContextElement((XulElement) editor.getComponent());
	            }


//	            Properties ctx = isDetailPane() ? new GridRowCtx(Env.getCtx(), gridTab, gridTab.getCurrentRow())
//	            	: gridPanelFields[i].getVO().ctx;
	            //check context
//				if (!gridPanelFields[i].isDisplayedGrid() ||
//					!gridPanelFields[i].isDisplayed(ctx, true))
//				{
//					editor.setVisible(false);
//				}
				editor.setReadWrite(gridPanelFields[i].isEditableGrid(true));


			}
			editing = true;

			GridTableListModel model = (GridTableListModel) grid.getModel();
			model.setEditing(true);

		}
	}

//	private boolean isDetailPane() {
//		Component parent = grid.getParent();
//		while (parent != null) {
//			if (parent instanceof DetailPane) {
//				return true;
//			}
//			parent = parent.getParent();
//		}
//		return false;
//	}

	/**
	 * @see RowRendererExt#getControls()
	 */
	public int getControls() {
		return DETACH_ON_RENDER;
	}

	/**
	 * @see RowRendererExt#newCell(Row)
	 */
	public Component newCell(Row row) {
		return null;
	}

	/**
	 * @see RowRendererExt#newRow(Grid)
	 */
	public Row newRow(Grid grid) {
		return null;
	}

	/**
	 * @see RendererCtrl#doCatch(Throwable)
	 */
	public void doCatch(Throwable ex) throws Throwable {
	}

	/**
	 * @see RendererCtrl#doFinally()
	 */
	public void doFinally() {
	}

	/**
	 * @see RendererCtrl#doTry()
	 */
	public void doTry() {
	}

	/**
	 * set focus to first active editor
	 */
	public void focusToFirstEditor() {
		if (currentRow != null && currentRow.getParent() != null) {
			WEditor toFocus = null;
			WEditor firstEditor = null;
			if (defaultFocusField != null
					&& defaultFocusField.isVisible() && defaultFocusField.isReadWrite() && defaultFocusField.getComponent().getParent() != null
					&& !(defaultFocusField instanceof WImageEditor)) {
				toFocus = defaultFocusField;
			}
			else
			{
				for (WEditor editor : getEditors()) {
					if (editor.isVisible() && editor.getComponent().getParent() != null) {
						if (editor.isReadWrite()) {
							toFocus = editor;
							break;
						}
						if (firstEditor == null)
							firstEditor = editor;
					}
				}
			}
			if (toFocus != null) {
				focusToEditor(toFocus);
			} else if (firstEditor != null) {
				focusToEditor(firstEditor);
			}
		}
	}

	protected void focusToEditor(WEditor toFocus) {
		Component c = toFocus.getComponent();
		if (c instanceof EditorBox) {
			c = ((EditorBox)c).getTextbox();
		} else if (c instanceof NumberBox) {
			c = ((NumberBox)c).getDecimalbox();
		} else if (c instanceof Urlbox) {
			c = ((Urlbox) c).getTextbox();
		}
		((HtmlBasedComponent)c).focus();
	}

	/**
	 * set focus to next readwrite editor from ref
	 * @param ref
	 */
	public void focusToNextEditor(WEditor ref) {
		boolean found = false;
		for (WEditor editor : getEditors()) {
			if (editor == ref) {
				found = true;
				continue;
			}
			if (found) {
				if (editor.isVisible() && editor.isReadWrite()) {
					focusToEditor(editor);
					break;
				}
			}
		}
	}

	/**
	 *
	 * @param gridPanel
	 */
	public void setGridPanel(JPiereGridView gridPanel) {
		this.gridPanel = gridPanel;
	}

	static class RowListener implements EventListener<Event> {

		private Grid _grid;

		public RowListener(Grid grid) {
			_grid = grid;
		}

		public void onEvent(Event event) throws Exception {
			if (Events.ON_CLICK.equals(event.getName())) {
				if (Executions.getCurrent().getAttribute("gridView.onSelectRow") != null)
					return;
				Event evt = new Event(Events.ON_CLICK, _grid, event.getTarget());
				Events.sendEvent(_grid, evt);
				evt.stopPropagation();
			}
			else if (Events.ON_DOUBLE_CLICK.equals(event.getName())) {
				Event evt = new Event(Events.ON_DOUBLE_CLICK, _grid, _grid);
				Events.sendEvent(_grid, evt);
			}
			else if (Events.ON_OK.equals(event.getName())) {
				Event evt = new Event(Events.ON_OK, _grid, _grid);
				Events.sendEvent(_grid, evt);
			}
		}
	}

	/**
	 * @return boolean
	 */
	public boolean isEditing() {
		return editing;
	}

	/**
	 * @param windowPanel
	 */
	public void setADWindowPanel(JPiereAbstractADWindowContent windowPanel) {
		if (this.m_windowPanel == windowPanel)
			return;

		this.m_windowPanel = windowPanel;

		buttonListener = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				WButtonEditor editor = (WButtonEditor) event.getSource();
				Integer rowIndex = (Integer) editor.getComponent().getAttribute(GRID_ROW_INDEX_ATTR);
				if (rowIndex != null) {
					int newRowIndex = gridTab.navigate(rowIndex);
					if (newRowIndex == rowIndex) {
						m_windowPanel.actionPerformed(event);
					}
				} else {
					m_windowPanel.actionPerformed(event);
				}
			}
		};
	}

	@Override
	public void onEvent(Event event) throws Exception {
		if (event.getTarget() instanceof Cell) {
			Cell cell = (Cell) event.getTarget();
			if (cell.getSclass() != null && cell.getSclass().indexOf("row-indicator-selected") >= 0)
				Events.sendEvent(gridPanel, new Event(DetailPane.ON_EDIT_EVENT, gridPanel));
			else if (event.getData() != null && event.getData().toString().indexOf("row-indicator-selected") >= 0)
				Events.sendEvent(gridPanel, new Event(DetailPane.ON_EDIT_EVENT, gridPanel));
			else
				Events.sendEvent(event.getTarget().getParent(), event);
		} else if (event.getTarget() instanceof Checkbox) {
			Executions.getCurrent().setAttribute("gridView.onSelectRow", Boolean.TRUE);
			Checkbox checkBox = (Checkbox) event.getTarget();
			Events.sendEvent(gridPanel, new Event("onSelectRow", gridPanel, checkBox));
		}
	}
}
