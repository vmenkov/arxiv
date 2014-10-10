<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
   UploadPapers main=new UploadPapers(request,response);
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Uploading personal papers
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   if (main.error) {   %>  <%@include file="../include/error.jsp" %>


<%   } else {      %>

<h1>Uploading personal papers</h1>


     <%if (main.uploadCnt>0) { %>

     <h2>Papers uploaded</h2>

     <p>
     <%= main.uploadCnt%> PDF document(s) uploaded now.
     </p>

     <% }%>

<%= main.dirInfo() %>

<h2>Uploading personal papers</h2>
     
<p>To help My.ArXiv personalize your user experience, you can upload a
few papers, in PDF format, that have been written by you, or are of particular
interest to you. Our system will analyze their content in order to be
able to find recnet ArXiv papers that may be of interest to you.

<p>You can upload any number of papers at this time. After all papers
have been uploaded, and your initial recommendation list has been
created and displayed, you will not be allowed to upload more papers.

<p>There are three options for uploading a paper: 

<ul> 

<li>If it is on the web somewhere (including on arxiv.org!) you can
simply provide a URL for the PDF file.

<li>You can simply upload the PDF file.

<li>You can upload an HTML document that contains links to several PDF
documents (which themselves have to be on the web somewhere).

</ul>

<table border=1>
<tr> <td>
Option 1 - specify a URL for a PDF file
<form  action="uploadPapers.jsp" method="post">
URL: <input type="text" size="80" name="url">
<input type="submit" value="Load PDF from this URL" />
</form>
</tr>

<tr> <td>
Option 2 - upload a PDF file:  
<form  enctype="multipart/form-data" action="uploadPapers.jsp" method="post">
<input type="file" size="80" name="pdf">
<input type="submit" value="Upload PDF file" />
</form>
</tr>



</table>


<%   }      %>



<ul>
<li><a href="<%=main.cp%>">Main page</a>
<li><a href="<%=main.cp%>/personal/index.jsp">Your account</a>
<li><a href="<%=main.cp%>/personal/editUserFormSelf.jsp">Modify your account information</a>
</ul>


<icd:RU/>

</body>
</html>
