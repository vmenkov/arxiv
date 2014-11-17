    $(function() {
	    $( "#sortable" ).sortable({change: function( event, ui ) {
			
			var sortedIDs = $("#sortable").sortable("toArray");
			var articles = "";
			
			for(i = 0; i < sortedIDs.length; i++) {
			    if(i != (sortedIDs.length - 1)) {
				articles += sortedIDs[i] + ":";
			    } else {
				articles += sortedIDs[i];
			    }
			}
			
			$.get('/arxiv.tmp/JudgmentServlet?action=REORDER&src=SB&pl=3481&id=' + articles);

		    }});
	    $( "#sortable" ).disableSelection();
	    $( "#sortable" ).on( "sortchange", function( event, ui ) {} );

	});
