// This script is inserted into FilterServlet's output when it is 
// appropriate to open the Session-Based Recommendations Moving Panel
function openSBMovingPanel(cp) {
    var sbw = window.open(cp + '/sessionBased.jsp', 'MyArxivSB',
    'left=20,top=20,width=500,height=500,toolbar=0,scrollbars=1,location=0,resizable=1');
    sbw.blur();
    sbw.focus();
}

//window.onload=openSBMovingPanel;