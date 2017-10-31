module Jekyll
  class TabsBlock < Liquid::Block
    def initialize(tag_name, markup, tokens)
      super
      @attributes = {}

      markup.scan(/([\w\-\_]+)='([^']*)'/) do |key, value|
        @attributes[key] = value
      end
    end

    def render(context)
      site = context.registers[:site]
      converter = site.find_converter_instance(::Jekyll::Converters::Markdown)
      content = converter.convert(super(context))

      output = "<ul class=\"nav nav-tabs\" role=\"tablist\">"
      @attributes.each_with_index do |attribute, index|
          clazz = index == 0 ? 'active' : ''
          output += "<li role=\"presentation\" class=\"#{clazz}\"><a href=\"\##{attribute[0]}\" role=\"tab\" data-toggle=\"tab\">#{attribute[1]}</a></li>"
        end

      output += "</ul>"
      output += "<div class=\"tab-content\">"
      output += "#{content}"
      output += "</div>"
      output
    end
  end

  class TabBlock < Liquid::Block
    def initialize(tag_name, markup, tokens)
      super
      @attributes = {}

      markup.scan(/([\w\-\_]+)='([^']*)'/) do |key, value|
        @attributes[key] = value
      end
    end

    def render(context)
      site = context.registers[:site]
      converter = site.find_converter_instance(::Jekyll::Converters::Markdown)
      content = converter.convert(super(context))
      classes = 'tab-pane' + (@attributes['class'] ? " #{@attributes['class']}" : '')

      "<div role=\"tabpanel\" class=\"#{classes}\" id=\"#{@attributes['id']}\">#{content}</div>"
    end
  end
end

Liquid::Template.register_tag('tabs', Jekyll::TabsBlock)
Liquid::Template.register_tag('tab', Jekyll::TabBlock)