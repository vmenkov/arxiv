<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewFolder main=new ViewFolder(request, response, true);
   User actor = main.actor;
%>

<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Personal folder for user <%= main.actorUserName %>
</title>
</head>
<body>

<h1>Personal folder for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>

<h2>Pages added to the folder</h2>

<table border=1>
<tr>
<th>Time recorded
<th>Article
</tr>
 	<!-- %= Reflect.htmlHeaderRow(Action.class, true) % -->
<%	
	for(int i=0; i<main.list.size(); i++) {
	Action a =  main.list.elementAt(i);
	ArticleEntry e = main.entries.elementAt(i);
%>
	<!-- %= Reflect.htmlRow(a, true) % -->
	<tr>
	<td><%=a.getTime()%>
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