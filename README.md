## EpicHeads
Search over 17,000 unique, artistic heads which are perfect for builders and servers with the nice EpicHeads resource.</br>
Quality, performance, and support are my priorities for this resource.
</br>

## Developers
Here is an example with built-in methods for developers that want to use the developers API to code other resources.
```ruby
# Check if EpicHeads is installed and enabled.
if (EpicHeadsAPI.isEnabled()) {
Hooray();
}

# How you would get the heads in any way.
Head getHead(int id)
Set<String> getCategories()
List<Head> getAllHeads()
List<Head> getCategoryHeads(String category)
void searchHeads(String query, Consumer<List<Head>> onResult)
void downloadHead(String playerName, Consumer<Head> consumer)

# How you would use the heads in any way.
int getId()
String getName()
String getCategory()
double getCost()
ItemStack getItem()
ItemStack getItem(String displayName)
```
