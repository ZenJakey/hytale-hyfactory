package com.trinex.hyfactory.ui.configPage

import com.trinex.lib.api.itemtransport.BlockSide
import com.trinex.lib.api.itemtransport.ItemFilterMode
import com.trinex.lib.api.itemtransport.ItemTransportComponent
import com.trinex.lib.api.itemtransport.ItemTransportMode

object ItemTransportComponentConfigPage {
    val transferSpeedId = "transfer-speed"
    val applyId = "apply-btn"
    val resetId = "reset-btn"
    val closeId = "close-btn"

    fun modeId(side: BlockSide): String = "mode-${side.name.lowercase()}"

    fun filterId(side: BlockSide): String = "filter-${side.name.lowercase()}"

    fun filterModeId(side: BlockSide): String = "filter-mode-${side.name.lowercase()}"

    fun filterMetaId(side: BlockSide): String = "filter-meta-${side.name.lowercase()}"

    private val sideOrder =
        listOf(
            BlockSide.NORTH,
            BlockSide.SOUTH,
            BlockSide.EAST,
            BlockSide.WEST,
            BlockSide.UP,
            BlockSide.DOWN,
        )

    private val modeOptions =
        listOf(
            ItemTransportMode.DISABLED to "Disabled",
            ItemTransportMode.PULL to "Pull",
            ItemTransportMode.PUSH to "Push",
        )

    val html: String = htmlFor(ItemTransportComponent())

    fun htmlFor(component: ItemTransportComponent): String {
        val configRows = sideOrder.joinToString("\n") { side -> renderConfigRow(component, side) }

        return """
            <style>
                .root {
                    layout-mode: top;
                }
                .section-title {
                    font-weight: bold;
                    font-size: 18;
                }
                .section {
                    layout-mode: top;
                }
                .row {
                    layout-mode: left;
                    anchor-height: 34;
                    align: leftcenter;
                }
                .row.header {
                    anchor-height: 26;
                }
                .side-name {
                    anchor-width: 80;
                }
                .field-label {
                    anchor-width: 140;
                }
                .mode-select {
                    anchor-width: 140;
                }
                .filter-input {
                    anchor-width: 260;
                }
                .filter-select {
                    anchor-width: 140;
                }
                .filter-checkbox {
                    anchor-width: 90;
                }
                .col-title {
                    font-weight: bold;
                }
                .spacer {
                    anchor-height: 6;
                }
                .actions {
                    layout-mode: left;
                    anchor-height: 40;
                }
            </style>
            <div class="page-overlay">
                <div class="decorated-container" data-hyui-title="Item Pipe Configuration" style="anchor-width: 700; anchor-height: 500;">
                    <div class="container-contents">
                        <div class="root">
                            <div class="section">
                                <p class="section-title">Side Configuration</p>
                                <div class="row header">
                                    <p class="side-name col-title">Side</p>
                                    <p class="mode-select col-title">Mode</p>
                                    <p class="filter-input col-title">Filter</p>
                                    <p class="filter-select col-title">Filter Mode</p>
                                    <p class="filter-checkbox col-title">Match</p>
                                </div>
                                $configRows
                            </div>
                            <div class="actions">
                                <button id="$applyId" class="secondary-button">Apply</button>
                                <button id="$resetId" class="tertiary-button">Reset</button>
                                <button id="$closeId" class="tertiary-button">Close</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            """.trimIndent()
    }

    private fun renderConfigRow(
        component: ItemTransportComponent,
        side: BlockSide,
    ): String {
        val selectedMode = component.getSideMode(side)
        val options =
            modeOptions.joinToString("\n") { (mode, label) ->
                val selected = if (mode == selectedMode) "selected=\"true\"" else ""
                "<option value=\"${mode.name}\" $selected>$label</option>"
            }
        val filter = component.getSideFilter(side)
        val ids = filter?.ids?.joinToString(", ") ?: ""
        val mode = filter?.mode ?: ItemFilterMode.WHITELIST
        val matchMetadata = filter?.matchMetadata ?: false
        val whitelistSelected = if (mode == ItemFilterMode.WHITELIST) "selected=\"true\"" else ""
        val blacklistSelected = if (mode == ItemFilterMode.BLACKLIST) "selected=\"true\"" else ""
        val checked = if (matchMetadata) "checked=\"true\"" else ""

        return """
            <div class="row">
                <p class="side-name">${side.name.lowercase().replaceFirstChar { it.titlecase() }}</p>
                <select id="${modeId(side)}" class="mode-select" data-hyui-showlabel="true" value="${selectedMode.name}">
                    $options
                </select>
                <input id="${filterId(side)}" class="filter-input" type="text" value="${escapeAttribute(ids)}" placeholder="Item ids, comma-separated" />
                <select id="${filterModeId(side)}" class="filter-select" data-hyui-showlabel="true" value="${mode.name}">
                    <option value="WHITELIST" $whitelistSelected>Whitelist</option>
                    <option value="BLACKLIST" $blacklistSelected>Blacklist</option>
                </select>
                <div class="filter-checkbox">
                    <input id="${filterMetaId(side)}" type="checkbox" $checked />
                </div>
            </div>
            """.trimIndent()
    }

    private fun escapeAttribute(value: String): String = value.replace("\"", "&quot;")
}
