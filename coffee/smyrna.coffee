$.smyrnaCall = (method, params, callback) ->
  $.jsonRpc
    url: "http://localhost:8080/json-rpc"
    type: "POST"
    method: method
    params: params
    success: (obj) ->
               callback(obj.result) if not obj.error

show = (content) ->
  doc = $('#output').get(0).contentWindow.document.body
  $(doc).html(content)
  $(doc).find('.smyrna-match').css('background-color', '#ff0')

handler = () -> $.smyrnaCall('highlight', [$('#q').val()], show)

resizeFrame = () ->
  console.log($('.lower td').height())
  $('#output').height($('.lower td').height())

$(() ->
  $('#q').val('').keyup(handler).focus()
  $('window').resize(resizeFrame)
  resizeFrame()
)