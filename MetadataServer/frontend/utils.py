from rest_framework.views import exception_handler
from frontend.views import master_urls, site_content, UserSerializer

def custom_exception_handler(exc, context):
    # Call REST framework's default exception handler first,
    # to get the standard error response.
    response = exception_handler(exc, context)

    # Move content into 'page' namespace
    # Now add the HTTP status code to the response.
    if response is not None:
        response.data = {
            'page': {
                'name': 'Error',
                'code': response.status_code,
                'text': response.status_text,
                'detail': response.data['detail']
            },
            'context': {
                "urls": master_urls(),
                "site": site_content(context['request'].site),
                "user": UserSerializer(context['request'].user).data,
            }
        }

    return response


