# JSON-RPC

makeURL = (x) -> document.location.protocol + '//' + document.location.host + "/" + x

smyrnaCall = (method, params, callback) ->
  $.jsonRpc
    url: makeURL "json-rpc"
    type: "POST"
    method: method
    params: params
    success: (obj) ->
               callback(obj.result) if not obj.error

# Utils

matchWindow = -> $($('#output').get(0).contentWindow)
matchBody = -> $(matchWindow().get(0).document.body)
matches = -> matchBody().find('.smyrna-match')
window.matches = matches
window.matchBody = matchBody

# Models

Concordance = Backbone.Model.extend
  sset: (m) -> this.set m, silent: true
  nextMatch: ->
    current = this.get 'selectedMatch'
    next = if current? and current + 1 < matches().length then current + 1 else 0
    this.set selectedMatch: next
  prevMatch: ->
    current = this.get 'selectedMatch'
    next = if matches().length == 0 then null else
           if current > 0 then current - 1 else matches().length - 1
    this.set selectedMatch: next
  nextDocument: ->
    current = this.get 'selectedDocument'
    num = this.get 'numDocuments'
    next = if num == 0 then 0 else
           if current == num - 1 then 0 else current + 1
    this.set selectedDocument: next
  prevDocument: ->
    current = this.get 'selectedDocument'
    num = this.get 'numDocuments'
    next = if num == 0 then 0 else
           if current == 0 then num - 1 else current - 1
    this.set selectedDocument: next

window.concordance = new Concordance
  selectedMatch: null
  selectedDocument: 0
  numDocuments: 0
  query: ""

concordance.bind 'change:selectedMatch', (model, i) ->
  matchBody().find('.highlighted-match').removeClass('highlighted-match')
  newMatch = matches().get(i)
  $(newMatch).addClass('highlighted-match') if i?
  matchWindow().scrollTo newMatch, 'fast'

concordance.bind 'change:selectedDocument', (model, document) ->
  model.sset selectedMatch: null
  refreshQuery()

concordance.bind 'change:query', (model, query) ->
  model.sset selectedDocument: 0, selectedMatch: null
  refreshQuery()

# Rest

setInfo = (info) ->
  info = 'Wpisz formę podstawową słowa, które chcesz wyszukać' if not info
  $('#info').html(info)

refreshQuery = -> smyrnaCall 'highlight', [concordance.get('query'), concordance.get('selectedDocument')], show

show = (content) ->
  matchBody().html content.html
  concordance.set numDocuments: content.count
  link = matchWindow().get(0).document.createElement('link')
  $(link).attr
    href: makeURL 'results.css'
    rel: 'stylesheet'
    type: 'text/css'
  matchBody().append(link)
  if concordance.get('query') != ''
    if content.count == 0
      setInfo('Nie znaleziono dokumentów')
    else
      setInfo('Znaleziono ' + content.count + ' dokumentów<br>' + matches().length + ' wyników w dokumencie ' + (1 + concordance.get 'selectedDocument'))
  matchBody().find('a').click(() -> false)

resizeFrame = () ->
  $('#content').height($(window).height() - 41)
  $('#output').height($('.lower td').height() - 1)

showTab = (tab) ->
  tab = tab.toLowerCase()
  $('.nav li').removeClass 'selected'
  $('#mi-' + tab).addClass 'selected'
  $('#content').children().hide()
  $('#' + tab).show()
  if tab == 'konkordancje'
    $('#q').focus()
    resizeFrame()

initMenu = ->
  $('.nav li').each((k, v) -> $(v).attr(id: 'mi-' + $(v).text().toLowerCase()).html('<a href="#">' + $(v).text() + '</a>'))
  $('.nav .about').click(-> $('#about-modal').reveal())
  $('.nav .tab').click(-> showTab $(this).text())
  $('#dupa').fileTree {acceptDirs: true, script: makeURL 'dir'}, (f) -> alert f    
  showTab 'korpusy'

$(() ->
  $('#q').val('').keyup(-> concordance.set query: $(this).val())
  $('window').resize(resizeFrame)
  $('#next').click(-> concordance.nextMatch())
  $('#prev').click(-> concordance.prevMatch())
  $('#pnext').click(-> concordance.nextDocument())
  $('#pprev').click(-> concordance.prevDocument())
  $('#content').children().hide()
  resizeFrame()
  setInfo()
  initMenu()
)