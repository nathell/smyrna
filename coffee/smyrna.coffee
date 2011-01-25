# Utils

matchWindow = -> $($('#output').get(0).contentWindow)
matchBody = -> $(matchWindow().get(0).document.body)
matches = -> matchBody().find('.smyrna-match')
window.matches = matches
window.matchBody = matchBody

# Models

Concordance = Backbone.Model.extend
  nextMatch: ->
    current = this.get 'selectedMatch'    
    next = if current? and current + 1 < matches().length then current + 1 else 0
    this.set
      selectedMatch: next
  prevMatch: ->
    current = this.get 'selectedMatch'    
    next = if matches().length == 0 then null else
           if current > 0 then current - 1 else matches().length - 1
    this.set
      selectedMatch: next

window.concordance = new Concordance
  selectedMatch: null

concordance.bind 'change:selectedMatch', (model, i) ->
  matchBody().find('.highlighted-match').removeClass('highlighted-match')
  newMatch = matches().get(i)
  $(newMatch).addClass('highlighted-match') if i?
  matchWindow().scrollTo(newMatch)

# JSON-RPC

$.smyrnaCall = (method, params, callback) ->
  $.jsonRpc
    url: "http://localhost:8080/json-rpc"
    type: "POST"
    method: method
    params: params
    success: (obj) ->
               callback(obj.result) if not obj.error

# Rest

getQuery = () -> $('#q').val()

setInfo = (info) ->
  info = 'Wpisz formę podstawową słowa, które chcesz wyszukać' if not info
  $('#info').text(info)

show = (content) ->
  matchBody().html content.html
  link = matchWindow().get(0).document.createElement('link')
  $(link).attr
    href: document.location.href + 'results.css'
    rel: 'stylesheet'
    type: 'text/css'
  matchBody().append(link)
  setInfo(matches().length + ' wyników w dokumencie') if getQuery() != ''
  matchBody().find('a').click(() -> false)

handler = () -> $.smyrnaCall('highlight', [getQuery()], show)

resizeFrame = () ->
  console.log($('.lower td').height())
  $('#output').height($('.lower td').height())

scrollNext = -> concordance.nextMatch

$(() ->
  $('#q').val('').keyup(handler).focus()
  $('window').resize(resizeFrame)
  $('#next').click(-> concordance.nextMatch())
  $('#prev').click(-> concordance.prevMatch())
  resizeFrame()
  setInfo()
)