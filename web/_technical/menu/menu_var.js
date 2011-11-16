/***********************************************************************************
*	(c) Ger Versluis 2000 version 5.411 24 December 2001 (updated Jan 31st, 2003 by Dynamic Drive for Opera7)
*	For info write to menus@burmees.nl		          *
*	You may remove all comments for faster loading	          *		
***********************************************************************************/

	var NoOffFirstLineMenus=3;			// Number of first level items
	var LowBgColor='#3C73FF';			// Background color when mouse is not over
	var LowSubBgColor='#0048FF';			// Background color when mouse is not over on subs
	var HighBgColor='#0048FF';			// Background color when mouse is over
	var HighSubBgColor='#A5BEFF';			// Background color when mouse is over on subs
	var FontLowColor='#FFFFFF';			// Font color when mouse is not over
	var FontSubLowColor='#FFFFFF';			// Font color subs when mouse is not over
	var FontHighColor='#FFFFFF';			// Font color when mouse is over
	var FontSubHighColor='#0048FF';			// Font color subs when mouse is over
	var BorderColor='transparent';			// Border color
	var BorderSubColor='transparent';			// Border color for subs
	var BorderWidth=0;				// Border width
	var BorderBtwnElmnts=0;			// Border between elements 1 or 0
	var FontFamily="arial, sans-serif"	// Font family menu items
	var FontSize=8;				// Font size menu items
	var FontBold=0;				// Bold menu items 1 or 0
	var FontItalic=0;				// Italic menu items 1 or 0
	var MenuTextCentered='center';			// Item text position 'left', 'center' or 'right'
	var MenuCentered='left';			// Menu horizontal position 'left', 'center' or 'right'
	var MenuVerticalCentered='top';		// Menu vertical position 'top', 'middle','bottom' or static
	var ChildOverlap=.05;				// horizontal overlap child/ parent
	var ChildVerticalOverlap=.05;			// vertical overlap child/ parent
	var StartTop=1;				// Menu offset x coordinate
	var StartLeft=0;				// Menu offset y coordinate
	var VerCorrect=0;				// Multiple frames y correction
	var HorCorrect=0;				// Multiple frames x correction
	var LeftPaddng=0;				// Left padding
	var TopPaddng=0;				// Top padding
	var FirstLineHorizontal=1;			// SET TO 1 FOR HORIZONTAL MENU, 0 FOR VERTICAL
	var MenuFramesVertical=1;			// Frames in cols or rows 1 or 0
	var DissapearDelay=1000;			// delay before menu folds in
	var TakeOverBgColor=1;			// Menu frame takes over background color subitem frame
	var FirstLineFrame='navig';			// Frame where first level appears
	var SecLineFrame='space';			// Frame where sub levels appear
	var DocTargetFrame='space';			// Frame where target documents appear
	var TargetLoc='MenuPos';				// span id for relative positioning
	var HideTop=0;				// Hide first level when loading new document 1 or 0
	var MenuWrap=1;				// enables/ disables menu wrap 1 or 0
	var RightToLeft=0;				// enables/ disables right to left unfold 1 or 0
	var UnfoldsOnClick=0;			// Level 1 unfolds onclick/ onmouseover
	var WebMasterCheck=0;			// menu tree checking on or off 1 or 0
	var ShowArrow=1;				// Uses arrow gifs when 1
	var KeepHilite=1;				// Keep selected path highligthed
	var Arrws=['tri.gif',5,10,'tridown.gif',10,5,'trileft.gif',5,10];	// Arrow source, width and height

function BeforeStart(){return}
function AfterBuild(){return}
function BeforeFirstOpen(){return}
function AfterCloseAll(){return}


// Menu tree
//	MenuX=new Array(Text to show, Link, background image (optional), number of sub elements, height, width);
//	For rollover images set "Text to show" to:  "rollover:Image1.jpg:Image2.jpg"

Menu1=new Array("<div class='menu_item_1'>Home</div>","http://www.pichugin.org/index.html","",0,32,65,"#000000","#000000","#000000","#000000","#000000");

Menu2=new Array("<div class='menu_item_2'>Curriculum Vit&#230;</div>","http://www.pichugin.org/cv.html","",0, 32, 65, "#000000","#000000","#000000","#000000","#000000");

Menu3=new Array("<div class='menu_item_1'>Резюме</div>","http://www.pichugin.org/cv_ru.html","",0, 32, 65, "#000000","#000000","#000000","#000000","#000000");

//  Menu3=new Array("<div class='menu_item_1'>Резюме</div>","","",0, 32, 65);
//	Menu3_1=new Array("<div class='menu_item_sub'>Dissertation</div>","http://www.germanschools.org/Conference/2008/Program.htm","",0,20,145);
//	Menu3_2=new Array("<div class='menu_item_sub'>Publications</div>","","",0,20,145);
//	Menu3_3=new Array("<div class='menu_item_sub'>Presentations</div>","","",0,20,145);

//  Menu4=new Array("<div class='menu_item_1'>Teaching</div>","","",3, 32, 65);
//	Menu4_1=new Array("<div class='menu_item_sub'>Teaching Philosophy</div>","http://www.germanschools.org/Conference/2008/Program.htm","",0,20,145);
//	Menu4_2=new Array("<div class='menu_item_sub'>Courses Taught</div>","","",0,20,145);
//	Menu4_3=new Array("<div class='menu_item_sub'>Tests and Materials Developed</div>","","",0,20,145);

//Menu4=new Array("<div class='menu_item'>Annual<br />Conference</div>","","",4, 32, 70);
//	Menu4_1=new Array("<div class='menu_item_sub'>Upcoming Conference</div>","http://www.germanschools.org/Conference/2008/Program.htm","",0,20,145);
//	Menu4_2=new Array("<div class='menu_item_sub'>Conference Archives</div>","","",6);
//		Menu4_2_1=new Array("<div class='menu_item_sub'>2007</div>","http://www.germanschools.org/Conference/2007/Program.htm","",0,20,60);
//		Menu4_2_2=new Array("<div class='menu_item_sub'>2006</div>","http://www.germanschools.org/Conference/2006/Program.htm","",0,20,60);
//		Menu4_2_3=new Array("<div class='menu_item_sub'>2005</div>","http://www.germanschools.org/Conference/2005/Program.htm","",0,20,60);
//		Menu4_2_4=new Array("<div class='menu_item_sub'>2004</div>","http://www.germanschools.org/Conference/2004/Program.htm","",0,20,60);
//		Menu4_2_5=new Array("<div class='menu_item_sub'>2003</div>","http://www.germanschools.org/Conference/2003/Program.htm","",0,20,60);
//		Menu4_2_6=new Array("<div class='menu_item_sub'>2002</div>","http://www.germanschools.org/Conference/2002/Program.htm","",0,20,60);
//	Menu4_3=new Array("<div class='menu_item_sub'>Photo Galleries</div>","","",3);
//		Menu4_3_1=new Array("<div class='menu_item_sub'>2007</div>","http://www.germanschools.org/Conference/Galleries/2007/index.htm","",0,20,60);
//		Menu4_3_2=new Array("<div class='menu_item_sub'>2005</div>","http://www.germanschools.org/Conference/Galleries/2005/index.htm","",0,20,60);
//		Menu4_3_3=new Array("<div class='menu_item_sub'>2004</div>","http://www.germanschools.org/Conference/Galleries/2004/index.htm","",0,20,60);
//	Menu4_4=new Array("<div class='menu_item_sub'>Participant Comments</div>","http://www.germanschools.org/Conference/2007/Testimonials.htm","",0);

//  Menu5=new Array("<div class='menu_item_2'>Academic<br />Awards</div>","","",0, 32, 60);
	
//  Menu6=new Array("<div class='menu_item_2'>Personal<br />Interests</div>","","",0, 32, 70);
//	Menu6_1=new Array("<div class='menu_item_sub'>For Children</div>","http://www.germanschools.org/Programs/Children.htm","",0,20,100);
//	Menu6_2=new Array("<div class='menu_item_sub'>For Adults</div>","http://www.germanschools.org/Programs/Adults.htm","",0);

//  Menu7=new Array("<div class='menu_item_1'>Contact</div>","","",0, 32, 70);
//	Menu7_1=new Array("<div class='menu_item_sub'>Publications</div>","http://www.germanschools.org/Publications/Publications.htm","",0,20,100);
//	Menu7_2=new Array("<div class='menu_item_sub'>Forms</div>","http://www.germanschools.org/Publications/Forms.htm","",0);

//  Menu8=new Array("<div class='menu_item_1'>Guestbook</div>","http://www.germanschools.org/Teachers_Login/Login.htm","",0, 32, 60);

//  Menu9=new Array("<div class='menu_item_2'>Photo<br />Gallery</div>","http://www.germanschools.org/Alumni_Login/Login.htm","",0, 32, 60);

//  Menu10=new Array("<div class='menu_item_2'>Other<br />Info</div>","http://www.germanschools.org/Dates/List.htm","",0, 32, 65);

//  Menu11=new Array("<div class='menu_item_2'>Useful<br />Links</div>","http://www.germanschools.org/Links/List.htm","",0, 32, 60);	