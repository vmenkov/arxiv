function flipCheckedOn(prefix) {
      $(prefix ).hide();
      $(prefix + '_checked').show();
}
function flipCheckedOff(prefix) {
      $(prefix + '_checked').hide();
      $(prefix ).show();
}
function ratingEntered(i,j, n) { 
   var k;
   for(k=0; k<n; k++) {
       if (k==j) {
          flipCheckedOn('#ratings' + i + '_' + k);
       } else {
          flipCheckedOff('#ratings' + i + '_' + k);
       }
    }
}
