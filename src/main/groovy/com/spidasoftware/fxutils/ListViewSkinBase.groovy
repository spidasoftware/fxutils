package com.spidasoftware.fxutils

import groovy.transform.CompileDynamic
import javafx.scene.control.skin.ListViewSkin
import javafx.scene.control.skin.VirtualContainerBase
import javafx.scene.control.skin.VirtualFlow
import groovy.transform.CompileStatic
import javafx.scene.control.ListView

import java.lang.reflect.Method

/**
 * Customized skin for list views, exposing some package-private methods
 */
@CompileStatic
abstract class ListViewSkinBase<T> extends ListViewSkin<T> {

	protected ListView listView

	ListViewSkinBase(ListView listView) {
		super(listView)
		this.listView = listView
	}

	protected double getVirtualFlowPreferredHeight(int rowsToCount) {
		// Have to call getVirtualFlowPreferredHeight this way because it's package-private
		Method m = VirtualContainerBase.class.getDeclaredMethod("getVirtualFlowPreferredHeight", int.class)
		m.setAccessible(true)
		return  (double) m.invoke(this, rowsToCount)
	}

	protected double getMaxCellWidth(int rowsToCount) {
		// Have to call getMaxCellWidth this way because it's package-private
		Method m = VirtualContainerBase.class.getDeclaredMethod("getMaxCellWidth", int.class)
		m.setAccessible(true)
		return  (double) m.invoke(this, rowsToCount)
	}

	@CompileDynamic // instead of casting the return values from ReflectionUtils.executePrivateMethod
	protected void ensureIndexIsVisible(int index) {
		if(index >= 0 && virtualFlow != null) {
			Integer firstVisibleIndex = ReflectionUtils.executePrivateMethod(VirtualFlow, virtualFlow, "getFirstVisibleCellWithinViewport")?.index
			Integer lastVisibleIndex = ReflectionUtils.executePrivateMethod(VirtualFlow, virtualFlow, "getLastVisibleCellWithinViewport")?.index
			if(firstVisibleIndex >= 0 && lastVisibleIndex >= 0 && (index < firstVisibleIndex || index > lastVisibleIndex)) {
				listView.scrollTo(index)
			}
		}
	}
}
