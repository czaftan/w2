<html>
<head>
<script src='http://code.jquery.com/jquery-2.1.4.js'
	type="text/javascript"></script>
<script
	src='http://cdn.datatables.net/1.10.7/js/jquery.dataTables.min.js'
	type="text/javascript"></script>
<script>
	var oTable;
	var uri = "";
	$(document).ready(function() {
		oTable = $('#table').DataTable({});

		// Dotaze se na link na data
		$.ajax({
			url : "getAll",
		}).done(function(data) {
			createTableData(data);
		});
	});

	function createTableData(data) {
		$.ajax({
			url : data,
			crossDomain : true,
			success : function(data) {
				$('#table').dataTable().fnAddData(JSON.parse(data));
			},
			error : function(xhr, ajaxOptions, thrownError) {
				if (xhr.status == 404) {
					setTimeout(function() {
						createTableData(data);
					}, 1000);
				}
			}
		});
	}

	$(document).on("click", "button.screenshot", function() {
		var self = $(this);
		var id = self.attr("id");
		getPicture(id);
	});

	$(document).on("click", "button.resolve", function() {
		var self = $(this);
		var id = self.attr("id");
		$.ajax({
			url : "resolve?id=" + id,
			success : function(data) {
				$("#table #" + id + ".resolve").attr("disabled", "disabled");
			}
		});
	});

	$(document).on("click", "button.cancel", function() {
		var self = $(this);
		var id = self.attr("id");
		$.ajax({
			url : "delete?id=" + id,
			success : function(data) {
				$("#table #" + id + ".resolve").attr("disabled", "disabled");
			}
		});
	});

	function getPicture(id) {
		$.ajax({
			url : "getPicture?id=" + id,
		}).done(function(data) {
			tryGetPicture(data);
		});
	}
	function tryGetPicture(data) {
		$.ajax({
			url : data,
			crossDomain : true,
			success : function() {
				$("#img").attr("src", data);
			},
			error : function(xhr, ajaxOptions, thrownError) {
				if (xhr.status == 404 || xhr.status == 0) {
					setTimeout(function() {
						tryGetPicture(data);
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
				<th>ID</th>
				<th>Message</th>
				<th>Page</th>
				<th>Application</th>
				<th>User</th>
				<th>Comment</th>
				<th>Candidates</th>
				<th>Screenshot</th>
				<th>Cancel</th>
				<th>Resolve</th>
			</tr>
		</thead>
	</table>
	<img id='img' src='' />
</body>
</html>
