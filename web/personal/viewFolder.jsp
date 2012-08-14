<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.html.RatingButton" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewFolder main=new ViewFolder(request, response, true);
   User actor = main.actor;
%>

<html>
<head>
<meta name="Description" content="List of articles in the user's personal folder"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Personal folder for user <%= main.actorUserName %>
</title>
<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
</head>
<body>

<h1>Personal folder for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>

<h2>Pages currently in the folder</h2>

<P>Your personal folder contains the following <%=main.list.size()%> articles.</p>

<table border=1>
<tr>
<th>No.
<th>Time added / Remove?
<th>Article
</tr>
 	<!-- %= Reflect.htmlHeaderRow(Action.class, true) % -->
<%

	for(int i=0; i<main.list.size(); i++) {
	Action a =  main.list.elementAt(i);
	ArticleEntry e = main.entries.elementAt(i);
%>
	<tr id="result<%=e.i%>">
	<td><%=(i+1)%>
	<td><%=a.getTime()%> <br>

	<a id="remove<%=e.i%>"
href="#" title="Remove from the folder"
onclick="$.get('<%=RatingButton.judge("..", e.id, Action.Op.REMOVE_FROM_MY_FOLDER, main.asrc)%>', 
function(data) { $('#result<%=e.i%>').hide(100);} )"
><img src="../_technical/images/bin.png" 
longdesc="Remove this document from your personal folder"
class="icon_instruction">Remove from the folder</a>

	<td>
	<% if (e!=null) { %>
	 <%= e.idline %> 
	 [<a href ="<%=main.urlAbstract(e.id)%>">Abstract</a>]
	 [<a href="<%=main.urlPDF(e.id)%>">PDF/PS/etc</a>] </br>
	<%= e.titline %><br />
			<%= e.authline %><br />
			<%= e.commline %><br />
			<%= e.subjline %><br />
	
	<% } else { %>
	   <%=a.getArticle()%>
	<% }  %>

	</tr>
 <%  }  %>
</table></p>


<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>