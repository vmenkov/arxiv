<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
   ResetPassword main=new ResetPassword(request,response);
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Resetting password
</title>
<jsp:include page="include/date-head-include.html" />
</head>
<body>

<% 
   if (main.error) {   %>  <%@include file="include/error.jsp" %>

<%   } else {      %>

<h1>Password reset</h1>

<p>Password for user account <em><%=main.uname%></em> has been reset.</p>

<p>The new password has been sent to the email address <em><%=main.email%></em>.
</p>

<p>It should arrive in an email message sent from the administrator's address, <em><%=main.businessEmail%></em>.

<p>When you have received the message with the new password, you can try to <a href="login2.jsp">log in again</a>.

<%   }      %>

<icd:RU/>

</body>
</html>
