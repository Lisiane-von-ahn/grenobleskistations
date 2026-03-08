import base64
from django import template

register = template.Library()

@register.filter(name='b64encode')
def b64encode(value):
    """Encodes a binary value to base64."""
    if isinstance(value, memoryview):
        value = value.tobytes()
    elif isinstance(value, bytearray):
        value = bytes(value)

    if isinstance(value, bytes):
        return base64.b64encode(value).decode('utf-8')
    return ''
