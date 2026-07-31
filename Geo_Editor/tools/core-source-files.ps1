function Get-GeoEditorCoreSourceFiles {
	param([Parameter(Mandatory = $true)][string]$Root)

	$files = [System.Collections.Generic.List[string]]::new()
	foreach ($directory in @(
		'src\main\java\com\geo\core',
		'java\com\geo\blocks',
		'java\com\geo\cells',
		'java\com\exceptions'
	)) {
		Get-ChildItem -LiteralPath (Join-Path $Root $directory) -Filter '*.java' -File |
			ForEach-Object { $files.Add($_.FullName) }
	}

	foreach ($relativePath in @(
		'java\com\geo\GeoBlock.java',
		'java\com\geo\GeoCell.java',
		'java\com\geo\GeoEngine.java',
		'java\com\geo\GeoRegion.java',
		'java\com\geo\RegionLoadManager.java',
		'java\com\geo\RegionStreamingPolicy.java',
		'java\com\geo\RegionWindowPlan.java',
		'java\com\geo\SelectionNavigationPolicy.java',
		'java\com\util\GeoByteBuffer.java',
		'java\com\util\GeoReader.java',
		'java\com\util\GeoStreamReader.java',
		'java\com\util\GeoStreamWriter.java',
		'java\com\util\GeoWriter.java',
		'java\com\util\MemoryUsage.java',
		'java\com\util\PerformanceMetrics.java',
		'java\com\util\Util.java',
		'java\com\util\WindowsProcessMemory.java'
	)) {
		$path = Join-Path $Root $relativePath
		if (-not (Test-Path -LiteralPath $path)) {
			throw "Required core source is missing: $relativePath"
		}
		$files.Add((Resolve-Path -LiteralPath $path).Path)
	}

	return @($files | Sort-Object -Unique)
}
