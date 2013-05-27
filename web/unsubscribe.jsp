<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Updating your account information
</title>
<jsp:include page="include/date-head-include.html" />
</head>
<body>

<% 
   EditUser main=new EditUser(request,response,  EditUser.Mode.UNSUBSCRIBE);
   if (main.error) {   %>  <%@include file="include/error.jsp" %>
<%   } else {      %>

<h1>Unsubscribed</h1>

<p>User <strong><%=main.uname%></strong> unsubscribed from email notifications.</p>


<%   }      %>

<icd:RU/>

</body>
</html>
