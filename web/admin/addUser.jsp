<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<% 
   AddUser main=new AddUser(request,response);
%>


<html>
<head>
<title>Adding/editing a User
</title>
</head>
<body>

<h1>Adding/editing a User entry</h1>

<%  if (main.error) {  %>  <%@include file="../include/error.jsp" %>
<%  } else {      %>
<p>Message: <%= main.infomsg%> </p>
<h3>Read back the new entry:</h3>

<table border=1><tr><td>
<%= main.reRead.reflectToString() %>
</table>

<h3>What do you want to do next?</h3>

<ul>

<li><A href="manageUsers.jsp">Back to the User Management Form</a>

</ul>

<%	
}     
%>

<icd:RU/>
</body>
</html>