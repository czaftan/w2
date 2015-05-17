<html>
<head>
<script src='http://code.jquery.com/jquery-2.1.4.js'
	type="text/javascript"></script>
<script
	src='http://cdn.datatables.net/1.10.7/js/jquery.dataTables.min.js'
	type="text/javascript"></script>
<script>
	var oTable;
	$(document).ready(function() {
		oTable = $('#table').DataTable({});

		// Dotaze se na link na data
		$.ajax({
			url : "getAll",
		}).done(function(data) {
			// Obnovi data v tabulce
			oTable.ajax.url(data);
			createTableData(data);
		});
	});

	function createTableData(data) {
		$.ajax({
			url : data,
			success : function(data) {
				oTable.ajax.reload();
			},
			error : function(xhr, ajaxOptions, thrownError) {
				if (xhr.status == 404) {
					setTimeout(function() {
						createTableData(data)
					}, 1000);
				}
			}
		});
	}
</script>
</head>
<body>
	<table id='table'>
		<thead>
			<tr>
				<th>Test</th>
			</tr>
		</thead>
	</table>
</body>
</html>
