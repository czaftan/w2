var oTable;
$(document).ready(function() {
	oTable = $('#example').dataTable({
		"ajax" : ""
	});

	// Dotaze se na link na data
	$.ajax({
		url : "getData",
	}).done(function(data) {
		// Obnovi data v tabulce
		oTable.fnReloadAjax(createTableData(data));
	});
});

function createTableData(data) {
	var tableData = [];
	data = JSON.parse(data);
	$.each(data, function() {
		var row = []
	});
	return tableData;
}