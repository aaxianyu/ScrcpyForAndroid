package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerViewModelTest {

    private fun newVm(): AppManagerViewModel =
        AppManagerViewModel(
            initialHideVersion = false,
            initialHideApkSize = false,
            initialHideDisabled = false,
        )

    @Test
    fun clearLocalSelection_onlyClearsSelection_keepsQueryAndBatch() {
        val vm = newVm()
        vm.onLocalPickerQueryChange("we")
        vm.toggleLocalSelection("com.a")
        vm.localBatchMode = true
        vm.clearLocalSelection()

        assertTrue(vm.localPickerQuery == "we")
        assertTrue(vm.localBatchMode)
        assertTrue(vm.localPickerSelection.isEmpty())
    }

    @Test
    fun clearLocalPickerSession_clearsSelectionQueryAndBatch() {
        val vm = newVm()
        vm.onLocalPickerQueryChange("we")
        vm.toggleLocalSelection("com.a")
        vm.localBatchMode = true
        vm.clearLocalPickerSession()

        assertEquals("", vm.localPickerQuery)
        assertFalse(vm.localBatchMode)
        assertTrue(vm.localPickerSelection.isEmpty())
    }

    @Test
    fun toggleLocalSelection_addsAndRemoves() {
        val vm = newVm()
        vm.toggleLocalSelection("com.a")
        vm.toggleLocalSelection("com.b")
        assertEquals(2, vm.localPickerSelection.size)

        vm.toggleLocalSelection("com.a")
        assertEquals(1, vm.localPickerSelection.size)
        assertTrue(vm.localPickerSelection.contains("com.b"))
    }
}
