<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   EditInvitation main=new EditInvitation(request,response);
%>
<html>
<head>
<title>Creating or editing an invitation</title>

</head>
<body>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp"%>

<p>
To try again, please go back using the "Back" button in your web browser,
and reenter correct data.
</p>

<%   } else {        
%>

<% if (main.mode==EditInvitation.Mode.CREATE) { %>
<h1>Created new invitation no. <em><%=main.id%></em></h1>
<% } else { %>
<h1>Updated invitation no. <em><%=main.id%></em></h1>
<% } %>


<p>
<table border="1">
<tr>
<td><%= main.o.reflectToString()  %></td>	
</tr>
</table>
</p>

<p>Invitation code to post in URLs: <%= main.o.getCode()%>
</p>

<% } %>

<p>
Back to the <a href="invitations.jsp">Invitation Manager</a>
</p>

<hr>
<div><small>System messages:<pre>
<%= main.infomsg%>
</pre>
</small></div>

<icd:RU/>

</body>
</html>