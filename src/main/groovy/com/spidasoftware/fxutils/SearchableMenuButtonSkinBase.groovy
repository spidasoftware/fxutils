package com.spidasoftware.fxutils

import com.sun.javafx.scene.control.LabeledImpl
import com.sun.javafx.scene.control.behavior.MenuButtonBehaviorBase
import com.sun.javafx.util.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import javafx.beans.property.ReadOnlyProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ListChangeListener.Change
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Bounds
import javafx.geometry.HPos
import javafx.geometry.Point2D
import javafx.geometry.VPos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.PopupControl
import javafx.scene.control.Skin
import javafx.scene.control.SkinBase
import javafx.scene.control.Skinnable
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.Callback
import org.apache.commons.lang3.StringUtils

import java.lang.reflect.Method
import java.util.function.Predicate

/**
 * A custom skin for MenuButtons that have a searchable popup list.
 *
 * The default button skin is copied from {@link javafx.scene.control.skin.MenuButtonSkinBase}.
 * I couldn't subclass it and just override the popup, but we still want the button to look the same as the default skin.
 *
 * A lot of the popup logic is copied and modified from {@link javafx.scene.control.skin.ComboBoxPopupControl}.
 */
@Slf4j
@CompileStatic
abstract class SearchableMenuButtonSkinBase<C extends MenuButton, B extends MenuButtonBehaviorBase<C>> extends SkinBase<C> {

	// ---- Copied from MenuButtonSkinBase -----------------------------------------------------------------------------
	protected final LabeledImpl label
	protected final StackPane arrow
	protected final StackPane arrowButton
	protected boolean behaveLikeButton = false
	// ---- Copied from MenuButtonSkinBase -----------------------------------------------------------------------------

	// The popup components.
	protected PopupControl popup
	protected VBox vBox = new VBox()
	protected TextField textField
	protected ListView<MenuItem> listView
	protected ListViewSkinBase<MenuItem> listViewSkin
	// We have to show, hide then show the popup again when the user clicks on the button so that it sizes correctly.
	// When we hide we don't want to set showing on the control to false.
	protected boolean hidingForSizing = false

	// The filtered list of items in the MenuButton, sets the predicate when the user changes the text in the textField.
	protected FilteredList<MenuItem> filteredList
	protected final ObservableList<MenuItem> items // The items available in the popup and can be filtered
	protected final ObservableList<MenuItem> defaultMenuItems// MenuItems that are always available.
	protected boolean popupNeedsReconfiguring = true // When items change or the button moves the popup needs reconfigured.
	protected MenuButton control
	protected int maxRowsInListView = 10 // The max number of rows to show in the listView, this isn't exposed anywhere.

	protected B behavior = null

	protected Callback<ListView<MenuItem>, ListCell<MenuItem>> cellFactory = new Callback<ListView<MenuItem>, ListCell<MenuItem>>() {
		@Override
		ListCell<MenuItem> call(ListView<MenuItem> param) {
			return new MenuButtonListCell()
		}
	}

	private class MenuButtonListCell extends ListCell<MenuItem> {
		@Override
		protected void updateItem(MenuItem menuItem, boolean empty) {
			super.updateItem(menuItem, empty)
			setGraphic(null)
			this.styleClass.remove("defaultMenuItem")

			if((menuItem != null) && !empty) {
				setOnMouseClicked({
									  SearchableMenuButtonSkinBase.this.triggerItemOnAction(menuItem)
									  SearchableMenuButtonSkinBase.this.hide()
								  } as EventHandler<MouseEvent>)
				if (SearchableMenuButtonSkinBase.this.defaultMenuItems.contains(menuItem)) {
					this.styleClass.add("defaultMenuItem")
				}
				setText(menuItem.text)
			} else {
				setOnMouseClicked(null) // Clear out the onMouseClicked
				setText(null)
			}
		}
	}

	protected abstract TextField createTextField()

	protected abstract ListViewSkinBase<MenuItem> createListViewSkin(ListView listView)

	protected SearchableMenuButtonSkinBase(C control, B behavior, ObservableList<MenuItem> items, ObservableList<MenuItem> defaultMenuItems) {
		super(control)

		this.behavior = behavior

		this.control = control
		control.styleClass.add("searchableMenuButton")
		this.items = items
		this.defaultMenuItems = defaultMenuItems
		ObservableList<MenuItem> combinedList = FXCollections.observableArrayList()
		this.filteredList = new FilteredList<>(combinedList)

		textField = createTextField()

		combinedList.addAll(defaultMenuItems)
		combinedList.addAll(items)

		defaultMenuItems.addListener({ Change<? extends MenuItem> change ->
			while(change.next()) {
				if(change.wasRemoved()) {
					combinedList.removeAll(change.removed)
				}
				if(change.wasAdded()) {
					combinedList.addAll(change.from, change.addedSubList)
				}
			}
									 } as ListChangeListener)

		items.addListener({ Change<? extends MenuItem> change ->
			while(change.next()) {
				if(change.wasRemoved()) {
					combinedList.removeAll(change.removed)
				}
				if(change.wasAdded()) {
					combinedList.addAll(defaultMenuItems.size() + change.from, change.addedSubList)
				}
			}
						  } as ListChangeListener<MenuItem>)

		// ---- Copied from MenuButtonSkinBase -------------------------------------------------------------------------
		if (control.getOnMousePressed() == null) {
			control.addEventHandler(MouseEvent.MOUSE_PRESSED, { MouseEvent e ->
				getBehavior().mousePressed(e, behaveLikeButton)
			} as EventHandler<MouseEvent>)
		}

		if (control.getOnMouseReleased() == null) {
			control.addEventHandler(MouseEvent.MOUSE_RELEASED,  { MouseEvent e ->
				getBehavior().mouseReleased(e, behaveLikeButton)
			} as EventHandler<MouseEvent>)
		}

		/*
		 * Create the objects we will be displaying.
		 */
		label = new MenuLabeledImpl(getSkinnable())
		label.setMnemonicParsing(control.isMnemonicParsing())
		label.setLabelFor(control)

		arrow = new StackPane()
		arrow.getStyleClass().setAll("arrow")

		arrow.setMaxWidth(Region.USE_PREF_SIZE)
		arrow.setMaxHeight(Region.USE_PREF_SIZE)

		arrowButton = new StackPane()
		arrowButton.getStyleClass().setAll("arrow-button")
		arrowButton.getChildren().add(arrow)

		getChildren().clear()
		getChildren().addAll(label, arrowButton)

		getSkinnable().requestLayout()
		// ---- Copied from MenuButtonSkinBase -------------------------------------------------------------------------

		// Create the popup
		listView = createListView()
		listViewSkin = createListViewSkin(listView)
		listView.setSkin(listViewSkin)
		listView.setCellFactory(cellFactory)
		listView.setManaged(true)
		listView.setItems(filteredList)

		vBox.children.addAll(textField, listView)

		popup = new PopupControl()
		popup.setAutoHide(true)
		popup.setConsumeAutoHidingEvents(false)

		popup.setSkin(new Skin<Skinnable>() { // Copied from com.sun.javafx.scene.control.skin.ComboBoxPopupControl and modified return the vBox as the Node
			@Override
			Skinnable getSkinnable() {
				return SearchableMenuButtonSkinBase.this.getSkinnable()
			}

			@Override
			Node getNode() {
				return vBox
			}

			@Override
			void dispose() { }
		})

		// When the text changes we need to recreate the cells, requestLayout() wasn't working,
		// the cells that were filtered out all had a width of 16.0.
		bindUnidirectional({
							   filteredList.setPredicate(new MenuItemPredicate(textField.text)) // Filter the items
							   selectFirstNonDefaultItem() // Select the first item
							   sizePopup()
						   }, textField.textProperty())

		// Handles navigation in the list while focus stays on the text field.
		textField.onKeyPressed = { KeyEvent keyEvent ->
			switch (keyEvent.getCode()){
				case KeyCode.DOWN:
					listView.selectionModel.selectNext()
					listViewSkin.ensureIndexIsVisible(listView.selectionModel.selectedIndex)
					break
				case KeyCode.UP:
					listView.selectionModel.selectPrevious()
					listViewSkin.ensureIndexIsVisible(listView.selectionModel.selectedIndex)
					break
				case KeyCode.ENTER:
					triggerSelectedItemOnAction()
			// Fallthrough to hide
				case KeyCode.ESCAPE:
					getSkinnable().hide()
					break
			}
		}

		// --- Copied from com.sun.javafx.scene.control.skin.ComboBoxPopupControl and modified to use our bindings -----
		Closure layoutPosListener = { // Fix for RT-21207
			popupNeedsReconfiguring = true
			reconfigurePopup()
		}
		bindUnidirectional(layoutPosListener, getSkinnable().layoutXProperty())
		bindUnidirectional(layoutPosListener, getSkinnable().layoutYProperty())
		bindUnidirectional(layoutPosListener, getSkinnable().widthProperty())
		bindUnidirectional(layoutPosListener, getSkinnable().heightProperty())

		Cursor cursorOnEnter = null
		textField.onMouseEntered = {
			cursorOnEnter = textField.scene.cursor
			textField.scene.setCursor(Cursor.TEXT)
		} as EventHandler

		textField.onMouseExited = {
			textField.scene.setCursor(cursorOnEnter)
		} as EventHandler

		bindUnidirectional({
							   // RT-36966 - if skinnable's scene becomes null, ensure popup is closed
							   if(getSkinnable().getScene() == null) {
								   hide()
							   }
						   }, control.sceneProperty())
		// --- Copied from com.sun.javafx.scene.control.skin.ComboBoxPopupControl and modified to use our bindings -----

		// --- Copied from com.sun.javafx.scene.control.skin.MenuButtonSkinBase and modified to use our bindings -------
		bindUnidirectional({
							   if (getSkinnable().isShowing()) {
								   show()
							   } else {
								   hide()
							   }
						   }, control.showingProperty())

		bindUnidirectional({
							   // Handle tabbing away from an open MenuButton
							   if (!getSkinnable().isFocused() && getSkinnable().isShowing()) {
								   hide()
							   }
							   if (!getSkinnable().isFocused() && popup.isShowing()) {
								   hide()
							   }
						   }, control.focusedProperty())

		bindUnidirectional({
							   if (!hidingForSizing && !popup.isShowing() && getSkinnable().isShowing()) {
								   // Popup was dismissed. Maybe user clicked outside or typed ESCAPE.
								   // Make sure button is in sync.
								   getSkinnable().hide()
							   }
						   }, popup.showingProperty())
		// --- Copied from com.sun.javafx.scene.control.skin.MenuButtonSkinBase and modified to use our bindings -------
	}

	protected abstract void bindUnidirectional(Closure viewSetter, ReadOnlyProperty property)

	/**
	 * We want to select the first item that isn't a default item on show() and when filtering.
	 */
	protected void selectFirstNonDefaultItem() {
		if(items.size() > 0 && filteredList.size() > defaultMenuItems.size()) { // If we have non-default items available for selection.
			listView.selectionModel.select(defaultMenuItems.size())
		} else {
			listView.selectionModel.selectFirst()
		}
		listViewSkin.ensureIndexIsVisible(listView.selectionModel.selectedIndex)
	}

	/**
	 * Will trigger the onAction for the currently selected MenuItem and hide the popup.
	 */
	private void triggerSelectedItemOnAction() {
		triggerItemOnAction(listView.selectionModel.selectedItem)
	}

	/**
	 * Will trigger the onAction for the MenuItem and hide the popup.
	 */
	private static void triggerItemOnAction(MenuItem menuItem) {
		if(menuItem?.onAction != null) {
			menuItem.onAction.handle(new ActionEvent(menuItem, menuItem))
		}
	}

	/**
	 * computePrefWidth and computePrefHeight are copied from {@link javafx.scene.control.skin.ComboBoxListViewSkin}
	 * with some modifications.
	 */
	protected ListView<MenuItem> createListView() {
		return new ListView<MenuItem>() {
			@Override
			protected double computePrefWidth(double height) {
				double maxCellWidth = listViewSkin.getMaxCellWidth(items.size()) + 30
				double pw = Math.max(control.getWidth(), maxCellWidth)
				return Math.max(50, pw)
			}

			@Override
			protected double computePrefHeight(double width) {
				int rowsToDisplay = Math.min(maxRowsInListView, items.size())
				rowsToDisplay = Math.max(rowsToDisplay, 1)
				return listViewSkin.getVirtualFlowPreferredHeight(rowsToDisplay)
			}
		}
	}

	/**
	 * Copied from {@link javafx.scene.control.skin.ComboBoxPopupControl}.
	 */
	private void reconfigurePopup() {
		// RT-26861. Don't call getPopup() here because it may cause the popup
		// to be created too early, which leads to memory leaks like those noted
		// in RT-32827.
		if (popup == null) return

		final boolean isShowing = popup.isShowing()
		if (! isShowing) return

		if (! popupNeedsReconfiguring) return
		popupNeedsReconfiguring = false

		final Point2D p = getPrefPopupPosition()

		// We are setting the prefSize below, so always clear the size cache of the vBox before calculating the prefHeight and prefWidth.
		Method clearSizeCache = Parent.class.getDeclaredMethod("clearSizeCache")
		clearSizeCache.setAccessible(true)
		clearSizeCache.invoke(vBox)

		final double prefWidth = vBox.prefWidth(Region.USE_COMPUTED_SIZE)
		final double prefHeight = vBox.prefHeight(Region.USE_COMPUTED_SIZE)
		if (p.getX() > -1) popup.setAnchorX(p.getX())
		if (p.getY() > -1) popup.setAnchorY(p.getY())
		if (prefWidth > -1) popup.setMinWidth(prefWidth)
		if (prefHeight > -1) popup.setMinHeight(prefHeight)

		final Bounds b = vBox.getLayoutBounds()
		final double currentWidth = b.getWidth()
		final double currentHeight = b.getHeight()
		final double newWidth  = currentWidth != prefWidth ? prefWidth : currentWidth
		final double newHeight = currentHeight != prefHeight ? prefHeight : currentHeight

		if (newWidth != currentWidth || newHeight != currentHeight) {
			// Resizing content to resolve issues such as RT-32582 and RT-33700
			// (where RT-33700 was introduced due to a previous fix for RT-32582)
			vBox.resize(newWidth, newHeight)
			vBox.setMinSize(newWidth, newHeight)
			vBox.setPrefSize(newWidth, newHeight)
		}
	}

	/**
	 * Copied from {@link javafx.scene.control.skin.ComboBoxPopupControl}.
	 */
	private void sizePopup() {
		final Node popupContent = vBox

		if (popupContent instanceof Region) {
			// snap to pixel
			final Region r = (Region) popupContent

			Method clearSizeCache = Parent.class.getDeclaredMethod("clearSizeCache")
			clearSizeCache.setAccessible(true)
			clearSizeCache.invoke(listView)

			// 0 is used here for the width due to RT-46097
			double prefHeight = snapSize(r.prefHeight(0))
			double minHeight = snapSize(r.minHeight(0))
			double maxHeight = snapSize(r.maxHeight(0))
			double h = snapSize(Math.min(Math.max(prefHeight, minHeight), Math.max(minHeight, maxHeight)))
			double prefWidth = snapSize(r.prefWidth(h))
			double minWidth = snapSize(r.minWidth(h))
			double maxWidth = snapSize(r.maxWidth(h))
			double w = snapSize(Math.min(Math.max(prefWidth, minWidth), Math.max(minWidth, maxWidth)))

			popupContent.resize(w, h)
		} else {
			popupContent.autosize()
		}
	}

	/**
	 * Copied from {@link javafx.scene.control.skin.ComboBoxPopupControl} and modified to pass in the vBox.
	 */
	private Point2D getPrefPopupPosition() {
		return Utils.pointRelativeTo(getSkinnable(), vBox, HPos.CENTER, VPos.BOTTOM, 0, 0, true)
	}

	protected void show() {
		if (!popup.isShowing()) {

			Point2D point2D = getPrefPopupPosition()

			// ListView doesn't handle the number of items changing well, prefHeight isn't always right, workaround is to
			// show the popup, size it, hide it then show it.
			popup.show(getSkinnable(), point2D.x, point2D.y)

			sizePopup() // Size the popup before reconfiguring, reconfigurePopup() will reposition the popup if it goes off of the screen.
			popupNeedsReconfiguring = true
			reconfigurePopup()

			hidingForSizing = true
			popup.hide()
			hidingForSizing = false
			// Note: this does not handle the popupSide from the MenuButton, but I don't think that we need it.
			popup.show(getSkinnable(), point2D.x, point2D.y)

			sizePopup() // Size the popup before reconfiguring, reconfigurePopup() will reposition the popup if it goes off of the screen.
			popupNeedsReconfiguring = true
			reconfigurePopup()

			selectFirstNonDefaultItem()
			textField.requestFocus()
		}
	}

	protected void hide() {
		if(popup.isShowing()) {
			textField.clear()
			popup.hide()
		}
	}

	protected B getBehavior() {
		return behavior
	}

	// ---- Copied from com.sun.javafx.scene.control.skin.MenuButtonSkinBase -------------------------------------------
	private class MenuLabeledImpl extends LabeledImpl {
		MenuButton button
		MenuLabeledImpl(MenuButton b) {
			super(b)
			button = b
			addEventHandler(ActionEvent.ACTION,  { ActionEvent e ->
				button.fireEvent(new ActionEvent())
				e.consume()
			})
		}
	}
	// ---- Copied from com.sun.javafx.scene.control.skin.MenuButtonSkinBase -------------------------------------------

	// ---- Copied from com.sun.javafx.scene.control.skin.MenuButtonSkinBase with reconfigurePopup() call added --------
	@Override protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
		reconfigurePopup()
		return leftInset + label.minWidth(height)+ snapSize(arrowButton.minWidth(height))+ rightInset
	}

	@Override protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
		reconfigurePopup()
		return topInset + Math.max(label.minHeight(width), snapSize(arrowButton.minHeight(-1)))+ bottomInset
	}

	@Override protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
		reconfigurePopup()
		return leftInset+ label.prefWidth(height)+ snapSize(arrowButton.prefWidth(height))+ rightInset
	}

	@Override protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
		reconfigurePopup()
		return topInset + Math.max(label.prefHeight(width), snapSize(arrowButton.prefHeight(-1)))+ bottomInset
	}

	@Override protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
		reconfigurePopup()
		return getSkinnable().prefWidth(height)
	}

	@Override protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
		reconfigurePopup()
		return getSkinnable().prefHeight(width)
	}

	@Override protected void layoutChildren(final double x, final double y, final double w, final double h) {
		final double arrowButtonWidth = snapSize(arrowButton.prefWidth(-1))
		label.resizeRelocate(x, y, w - arrowButtonWidth, h)
		arrowButton.resizeRelocate(x+(w-arrowButtonWidth), y, arrowButtonWidth, h)
	}
	// ---- Copied from com.sun.javafx.scene.control.skin.MenuButtonSkinBase with reconfigurePopup() call added --------

	protected class MenuItemPredicate implements Predicate<MenuItem> {
		String query

		MenuItemPredicate(String query) {
			this.query = query
		}

		@Override
		boolean test(MenuItem item) {
			if (StringUtils.isBlank(query) || defaultMenuItems.contains(item)) {
				return true
			}

			String lowerCase = query.toLowerCase()
			String text = item.text?.toLowerCase()
			return text?.contains(lowerCase)
		}
	}
}