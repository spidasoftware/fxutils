package com.spidasoftware.fxutils

import javafx.scene.control.skin.MenuButtonSkin
import groovy.transform.CompileStatic
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.Node
import javafx.scene.control.MenuButton
import javafx.scene.control.skin.MenuButtonSkinBase
import javafx.scene.layout.Region

/**
 * Skin for a MenuButton that has no arrow.
 */
@CompileStatic
class MenuButtonWithoutArrowSkin extends MenuButtonSkin {

	protected Region labelAlias // "label" is package-private in MenuButtonSkinBase

	MenuButtonWithoutArrowSkin(MenuButton menuButton) {
		super(menuButton)

		// label and arrowButton are final and package-private in MenuButtonSkinBase
		def arrowButtonField = MenuButtonSkinBase.class.getDeclaredField("arrowButton")
		arrowButtonField.setAccessible(true)
		getChildren().remove(arrowButtonField.get(this))
		def labelField = MenuButtonSkinBase.class.getDeclaredField("label")
		labelField.setAccessible(true)
		labelAlias = labelField.get(this) as Region
		labelAlias.setPadding(new Insets(0))
		
		getSkinnable().requestLayout()
	}

	@Override protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
		return leftInset + labelAlias.minWidth(height) + rightInset
	}

	@Override protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
		return topInset + labelAlias.minHeight(width) + bottomInset
	}

	@Override protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
		return leftInset + labelAlias.prefWidth(height) + rightInset
	}

	@Override protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
		return topInset + labelAlias.prefHeight(width) + bottomInset
	}

	@Override protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
		return getSkinnable().prefWidth(height)
	}

	@Override protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
		return getSkinnable().prefHeight(width)
	}

	// this implementation is copied directly from SkinBase (and "fixed" to compile in Groovy)
	@Override protected void layoutChildren(final double contentX, final double contentY,
											final double contentWidth, final double contentHeight) {
		// By default simply sizes all managed children to fit within the space provided
		int max=children.size()
		for (int i=0; i<max; i++) {
			Node child = children.get(i)
			if (child.isManaged()) {
				layoutInArea(child, contentX, contentY, contentWidth, contentHeight, -1, HPos.CENTER, VPos.CENTER)
			}
		}
	}
}
