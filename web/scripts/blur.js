function BlurLinks(){
    lnks=document.getElementsByTagName('a');
    for(i=0;i<lnks.length;i++){
	lnks[i].onfocus=new Function("if(this.blur)this.blur()");
    }
}
