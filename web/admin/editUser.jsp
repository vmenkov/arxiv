<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<html>
<head>
<title>Updating your account information
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   EditUser main=new EditUser(request,response,  EditUser.Mode.EDIT_ANY);

   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>

<h1>Record updated</h1>

<p>Record updated for User_name = <em><%=main.r.getUser_name()%></em>:</p>

<p>
<table border=1>
<tr><%= User.header4cells()  %> </tr>
<tr><%= main.r.to4cells()  %> </tr>
</table>
</p>

<%   }      %>

<p align="center">
<a href="manageUsers.jsp">Back to the main User Management page</a>
</p>

<icd:RU/>

</body>
</html>