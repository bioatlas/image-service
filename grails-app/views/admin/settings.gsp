<!doctype html>
<html>
<head>
    <meta name="layout" content="adminLayout"/>
    <title>ALA Images - Admin - Settings</title>
</head>

<body>
    <content tag="pageTitle">Settings</content>
    <table class="table table-bordered table-striped">
        <g:each in="${settings}" var="setting">
            <tr>
                <td>
                    <small>
                        ${setting.name}
                    </small>
                </td>
                <td>
                    ${setting.description}
                </td>
                <td>
                    <g:if test="${setting.type == au.org.ala.images.SettingType.Boolean}">
                        <g:checkBox name="${setting.name}" data-size="small" checked="${Boolean.parseBoolean(setting.value)}" />
                    </g:if>
                    <g:else>
                        ${setting.value}
                    </g:else>
                </td>
            </tr>
        </g:each>
    </table>
</div>
</body>
</html>
