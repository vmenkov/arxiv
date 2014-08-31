// This script is inserted into FilterServlet's output when it is 
// appropriate to open the Session-Based Recommendations Moving Panel.
// The opening is done with some delay, to give server a better chance 
// to generate an updated rec list in time.
function openSBMovingPanelNow(cp) {
    //alert('Told to open SB popup now');
    var sbw = window.open(cp + '/sessionBased.jsp?popout=true', 'MyArxivSB',
    'left=20,top=20,width=500,height=500,toolbar=0,scrollbars=1,location=0,resizable=1');
    sbw.blur();
    sbw.focus();
}

function openSBMovingPanel(cp, delayMsec) {
    setTimeout(function() {
	 openSBMovingPanelNow(cp)
    },  delayMsec);
}

function checkSBAgainLater(url, delayMsec) {
    //alert('Told to wait for ' + delayMsec + ' msec and check again');
    setTimeout(function() {
	//alert('Now querying ' + url);
	$.get(url, function(data) {
	    //alert('From ' + url +', got back this: ' + data);
	    eval(data);})
    },  delayMsec);
}


//window.onload=openSBMovingPanel;