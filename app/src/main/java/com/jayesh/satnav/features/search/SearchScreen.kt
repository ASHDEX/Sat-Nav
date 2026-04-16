package com.jayesh.satnav.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jayesh.satnav.domain.model.PointOfInterest
import com.jayesh.satnav.domain.model.PoiCategory
import com.jayesh.satnav.domain.model.RecentSearch

/** Categories shown as quick-access filter chips. */
private val QuickCategories = listOf(
    PoiCategory.RESTAURANT,
    PoiCategory.FUEL_STATION,
    PoiCategory.HOSPITAL,
    PoiCategory.HOTEL,
    PoiCategory.SUPERMARKET,
    PoiCategory.PARKING,
    PoiCategory.PHARMACY,
    PoiCategory.BANK,
    PoiCategory.TRAIN_STATION,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onLocationSelected: (lat: Double, lon: Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Search bar ────────────────────────────────────────────────────
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search places, addresses…") },
            leadingIcon = {
                if (state.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to map")
                    }
                }
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearSearch) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { viewModel.performSearch(resetPagination = true) },
            ),
            shape = MaterialTheme.shapes.large,
        )

        // ── Error message ─────────────────────────────────────────────────
        state.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ── Category filter chips ─────────────────────────────────────────
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedCategory == null,
                onClick = { viewModel.selectCategory(null) },
                label = { Text("All") },
            )
            QuickCategories.forEach { category ->
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text(category.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }

        // ── Location row ──────────────────────────────────────────────────
        val currentLocation = state.currentLocation
        if (currentLocation != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "%.4f, %.4f".format(
                            currentLocation.first,
                            currentLocation.second,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Nearby",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Switch(
                        checked = state.useLocationSearch,
                        onCheckedChange = { viewModel.toggleLocationSearch() },
                    )
                }
            }
        }

        // ── Content area ──────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                // Autocomplete suggestions while typing (before explicit search)
                state.query.length >= 2
                    && state.autocompleteSuggestions.isNotEmpty()
                    && state.searchResults.isEmpty() -> {
                    AutocompleteList(
                        suggestions = state.autocompleteSuggestions,
                        onSelect = viewModel::selectAutocompleteSuggestion,
                    )
                }

                // Results after search
                state.searchResults.isNotEmpty() -> {
                    ResultsList(
                        results = state.searchResults,
                        currentLocation = state.currentLocation,
                        hasMore = state.hasMoreResults,
                        onPoiClick = { poi ->
                            onLocationSelected(poi.latitude, poi.longitude)
                        },
                        onFavoriteToggle = viewModel::toggleFavorite,
                        onLoadMore = viewModel::loadMoreResults,
                    )
                }

                // Loading
                state.isLoading || state.isSearching -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Empty query — show recent searches or placeholder
                state.query.isEmpty() -> {
                    if (state.recentSearches.isNotEmpty()) {
                        RecentSearchesList(
                            recents = state.recentSearches,
                            onSelect = viewModel::selectRecentSearch,
                            onClearAll = viewModel::clearRecentSearches,
                        )
                    } else {
                        EmptyQueryPlaceholder()
                    }
                }

                // Query entered but no results
                state.query.isNotEmpty() -> {
                    NoResultsPlaceholder(query = state.query)
                }
            }
        }
    }

    // ── POI detail bottom sheet ───────────────────────────────────────────
    if (state.selectedPoi != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closePoiDetails,
            sheetState = sheetState,
        ) {
            PoiDetailSheet(
                poi = state.selectedPoi!!,
                onFavoriteToggle = { viewModel.toggleFavorite(state.selectedPoi!!) },
                onDismiss = viewModel::closePoiDetails,
            )
        }
    }
}

// ── Autocomplete ──────────────────────────────────────────────────────────────

@Composable
private fun AutocompleteList(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    LazyColumn {
        items(suggestions) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(suggestion) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
        }
    }
}

// ── Results list ──────────────────────────────────────────────────────────────

@Composable
private fun ResultsList(
    results: List<PointOfInterest>,
    currentLocation: Pair<Double, Double>?,
    hasMore: Boolean,
    onPoiClick: (PointOfInterest) -> Unit,
    onFavoriteToggle: (PointOfInterest) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn {
        item {
            Text(
                text = "${results.size} result${if (results.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(results, key = { it.id }) { poi ->
            PoiResultRow(
                poi = poi,
                currentLocation = currentLocation,
                onClick = { onPoiClick(poi) },
                onFavoriteToggle = { onFavoriteToggle(poi) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
        }
        if (hasMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onLoadMore,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text("Load more")
                    }
                }
            }
        }
    }
}

@Composable
private fun PoiResultRow(
    poi: PointOfInterest,
    currentLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Category icon badge
        Card(
            modifier = Modifier.size(40.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = categoryIcon(poi.category),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Name + address + distance
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = poi.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            poi.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (currentLocation != null) {
                val distanceM = poi.distanceTo(currentLocation.first, currentLocation.second)
                Text(
                    text = formatDistance(distanceM),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Favorite star
        IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = if (poi.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (poi.isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Recent searches ───────────────────────────────────────────────────────────

@Composable
private fun RecentSearchesList(
    recents: List<RecentSearch>,
    onSelect: (RecentSearch) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearAll) {
                    Text("Clear all", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        items(recents, key = { it.id }) { recent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(recent) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = recent.query,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
        }
    }
}

// ── Placeholder states ────────────────────────────────────────────────────────

@Composable
private fun EmptyQueryPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search for places",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Find restaurants, fuel stations, hospitals, and more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NoResultsPlaceholder(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No results for \u201c$query\u201d",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Try a different search term or category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── POI detail bottom sheet ───────────────────────────────────────────────────

@Composable
private fun PoiDetailSheet(
    poi: PointOfInterest,
    onFavoriteToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = poi.category.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
            }
        }

        HorizontalDivider()

        poi.rating?.let { rating ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "%.1f".format(rating),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        poi.address?.let { DetailRow(icon = Icons.Outlined.Place, text = it) }
        poi.phone?.let { DetailRow(icon = Icons.Outlined.Place, text = it) }
        poi.website?.let { DetailRow(icon = Icons.Outlined.Search, text = it) }
        poi.openingHours?.let { DetailRow(icon = Icons.Outlined.History, text = it) }

        Spacer(modifier = Modifier.height(4.dp))

        FilledTonalButton(
            onClick = onFavoriteToggle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (poi.isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (poi.isFavorite) "Remove from favorites" else "Add to favorites")
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun categoryIcon(category: PoiCategory): ImageVector = when (category) {
    PoiCategory.RESTAURANT, PoiCategory.CAFE,
    PoiCategory.FAST_FOOD, PoiCategory.BAR, PoiCategory.PUB -> Icons.Outlined.Restaurant
    PoiCategory.FUEL_STATION -> Icons.Outlined.LocalGasStation
    PoiCategory.PARKING -> Icons.Outlined.LocalParking
    PoiCategory.BUS_STOP -> Icons.Outlined.DirectionsBus
    PoiCategory.TRAIN_STATION -> Icons.Outlined.Train
    PoiCategory.AIRPORT -> Icons.Outlined.Flight
    PoiCategory.HOTEL, PoiCategory.MOTEL,
    PoiCategory.HOSTEL, PoiCategory.GUEST_HOUSE -> Icons.Outlined.Hotel
    PoiCategory.SUPERMARKET, PoiCategory.CONVENIENCE_STORE,
    PoiCategory.BAKERY -> Icons.Outlined.ShoppingCart
    PoiCategory.PHARMACY, PoiCategory.PHARMACY_EMERGENCY -> Icons.Outlined.LocalPharmacy
    PoiCategory.HOSPITAL, PoiCategory.CLINIC -> Icons.Outlined.LocalHospital
    PoiCategory.BANK, PoiCategory.ATM -> Icons.Outlined.AccountBalance
    else -> Icons.Outlined.Place
}

private fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m"
    else -> "${"%.1f".format(meters / 1000)} km"
}
