<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Sending notification email...</title>
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   EmailSug main=new EmailSug(request,response);
   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (!main.sent){      %>

<h1>Email not sent</h1>

<p>Email <strong>not</strong> sent to user <strong><%=main.uname%></strong>. Please see the status line below for details.</p>

<%   } else {      %>

<h1>Email sent</h1>

<p>Email sent to user <strong><%=main.uname%></strong>.</p>

<%   }  %>

<hr> <p><small>System message: <%= main.infomsg%> </small></p> 

<icd:RU/>

</body>
</html>
