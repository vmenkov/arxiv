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

<% if (main.mode==EditInvitation.CREATE) { %>
<h1>Created a new invitation</h1>
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



<form method=post action="editInvitation.jsp">
<%= Tools.inputHidden(EditInvitation.ID, main.id) %> <br>

<p>
<table><tr><td>
<%= main.entryForm()%>
</td><td>
<!--space for the pop-up date selector -->
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
</td></tr></table>

</p>

<p>
<input type="submit" value="Submit request">
</p>

<% } %>

<icd:RU/>

</body>
</html>