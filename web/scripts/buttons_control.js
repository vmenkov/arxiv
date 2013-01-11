function flipCheckedOn(prefix) {
   // alert('hide ' + prefix);
      $(prefix ).hide();
  //  alert('show ' + prefix+ '_checked');
      $(prefix + '_checked').show();
}
function flipCheckedOff(prefix) {
      $(prefix + '_checked').hide();
      $(prefix ).show();
}

// array of button ordinals
var hideables = [];

function ratingEntered(i,myname) { 
    var k;
    //alert('RE i=' + i +', myname=' + myname);
    for(k=0; k<hideables.length; k++) {
	var name =  'ratings' + i + '_' +hideables[k];
	if (name!=myname) {
	    //alert('RE: off with ' + name);
            flipCheckedOff('#' + name);
	}
    }
    flipCheckedOn('#' +myname);
    $('#advice' + i).hide();
}

function setFolderSize(text) {
    //alert('SFS: ' + text);
    document.getElementById("folderSize").innerHTML=text;
}