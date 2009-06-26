#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
    <script type="text/javascript" src="${symbol_dollar}{pageContext.request.contextPath}/dojo/dojo.js.uncompressed.js"></script>
    <script type="text/javascript" src="application.js"></script>
    <script type="text/javascript">
        var config = {
            contextPath: '${symbol_dollar}{pageContext.request.contextPath}'
        };
    </script>
</head>
<body>

    <div id="body"></div>

</body>
</html>