package org.jellyfin.androidtv.data.homerows

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/*
 * DTOs of the personal fork server's HomeRows API (Custom Home Rows). See docs/home-rows.md.
 */

/** The id sent for new rows; the server assigns a real one on save. */
val EMPTY_ROW_ID: UUID = UUID(0, 0)

/** One row of a layout (server `HomeRowInstanceDto`). */
@Serializable
data class HomeRowInstance(
	@SerialName("Id") @Serializable(with = UUIDSerializer::class) val id: UUID,
	@SerialName("Provider") val provider: String,
	@SerialName("Title") val title: String? = null,
	@SerialName("Config") val config: JsonObject = JsonObject(emptyMap()),
	@SerialName("Enabled") val enabled: Boolean = true,
	@SerialName("IsAvailable") val isAvailable: Boolean = true,
)

/** The user's effective layout (server `HomeRowConfigurationDto`). */
@Serializable
data class HomeRowConfiguration(
	@SerialName("ApiVersion") val apiVersion: Int,
	@SerialName("Source") val source: String,
	@SerialName("IsCustomized") val isCustomized: Boolean = false,
	@SerialName("Rows") val rows: List<HomeRowInstance> = emptyList(),
)

/** A row type the server offers (server `HomeRowProviderDto`). */
@Serializable
data class HomeRowProvider(
	@SerialName("Id") val id: String,
	@SerialName("Name") val name: String,
	@SerialName("Description") val description: String = "",
	@SerialName("Schema") val schema: JsonObject = JsonObject(emptyMap()),
	@SerialName("Defaults") val defaults: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class HomeRowProviders(
	@SerialName("ApiVersion") val apiVersion: Int,
	@SerialName("Providers") val providers: List<HomeRowProvider> = emptyList(),
)

/** How a row asks to be drawn (server `HomeRowPresentation`). */
@Serializable
data class HomeRowPresentation(
	@SerialName("CardShape") val cardShape: String,
	@SerialName("PreferredImageType") val preferredImageType: String,
	@SerialName("FallbackImageType") val fallbackImageType: String? = null,
)

/** One page of a row's items (server `HomeRowItemsDto`). */
@Serializable
data class HomeRowItems(
	@SerialName("RowId") @Serializable(with = UUIDSerializer::class) val rowId: UUID,
	@SerialName("Title") val title: String? = null,
	@SerialName("Items") val items: List<BaseItemDto> = emptyList(),
	@SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
	@SerialName("StartIndex") val startIndex: Int = 0,
	@SerialName("Presentation") val presentation: HomeRowPresentation,
	@SerialName("Error") val error: String? = null,
)

@Serializable
internal data class HomeRowsSaveRequest(
	@SerialName("Rows") val rows: List<HomeRowInstance>,
)

@Serializable
internal data class HomeRowsValidationProblem(
	val errors: Map<String, List<String>> = emptyMap(),
)
